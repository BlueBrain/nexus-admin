package ch.epfl.bluebrain.nexus.admin.core.projects

import java.time.Clock

import cats.MonadError
import cats.syntax.all._
import ch.epfl.bluebrain.nexus.admin.core.Fault.{CommandRejected, Unexpected}
import ch.epfl.bluebrain.nexus.admin.core.config.AppConfig._
import ch.epfl.bluebrain.nexus.admin.core.organizations.Organizations
import ch.epfl.bluebrain.nexus.admin.core.persistence.PersistenceId
import ch.epfl.bluebrain.nexus.admin.core.resources.ResourceRejection._
import ch.epfl.bluebrain.nexus.admin.core.resources.Resources.Agg
import ch.epfl.bluebrain.nexus.admin.core.resources._
import ch.epfl.bluebrain.nexus.admin.core.types.Ref._
import ch.epfl.bluebrain.nexus.admin.ld.Const._
import ch.epfl.bluebrain.nexus.admin.ld.JsonLD._
import ch.epfl.bluebrain.nexus.admin.ld.{Const, JsonLD}
import ch.epfl.bluebrain.nexus.admin.refined.project._
import ch.epfl.bluebrain.nexus.commons.shacl.validator.ShaclValidatorErr.{CouldNotFindImports, IllegalImportDefinition}
import ch.epfl.bluebrain.nexus.commons.shacl.validator.{ShaclSchema, ShaclValidator}
import ch.epfl.bluebrain.nexus.commons.sparql.client.SparqlClient
import io.circe.Json
import journal.Logger

/**
  * Bundles operations that can be performed against a project using the underlying persistence abstraction.
  *
  * @param organizations  organizations operation bundle
  * @param agg            aggregate for projects
  * @param F         a MonadError typeclass instance for ''F[_]''
  * @tparam F the monadic effect type
  */
class Projects[F[_]](organizations: Organizations[F], agg: Agg[F], sparqlClient: SparqlClient[F])(
    implicit
    F: MonadError[F, Throwable],
    logger: Logger,
    clock: Clock,
    validator: ShaclValidator[F],
    config: ProjectsConfig,
    persistenceId: PersistenceId[ProjectReference])
    extends Resources[F, ProjectReference](agg, sparqlClient) {

  /**
    * Certain validation to take place during creation operations.
    *
    * @param id    the identifier of the resource
    * @param value the json payload of the resource
    * @return () or the appropriate rejection in the ''F'' context
    */
  override def validateCreate(id: ProjectReference, value: Json): F[Unit] = validate(id, value)

  /**
    * Certain validation to take place during update operations.
    *
    * @param id    the identifier of the resource
    * @param value the json payload of the resource
    * @return () or the appropriate rejection in the ''F'' context
    */
  override def validateUpdate(id: ProjectReference, value: Json): F[Unit] = validate(id, value)

  private def validate(id: ProjectReference, value: Json): F[Unit] = {
    for {
      _ <- organizations.validateUnlocked(id.organizationReference)
      _ <- validatePayload(id, value)
    } yield ()
  }

  private def validatePayload(id: ProjectReference, value: JsonLD): F[Unit] = {
    value
      .mapObject(_.add(Const.`@id`, Json.fromString(s"${config.namespace}${id.value}")))
      .appendContext(projectContext)
      .add(rdf.tpe, nxv.Project)
      .add(nxv.label, id.value)
      .apply() match {
      case Some(merged) =>
        validator(ShaclSchema(projectSchema), merged)
          .flatMap { report =>
            if (report.conforms) F.pure(())
            else F.raiseError[Unit](CommandRejected(ShapeConstraintViolations(report.result.map(_.reason))))
          }
          .recoverWith {
            case CouldNotFindImports(missing)     => F.raiseError(CommandRejected(MissingImportsViolation(missing)))
            case IllegalImportDefinition(missing) => F.raiseError(CommandRejected(IllegalImportsViolation(missing)))
          }
      case None => F.raiseError(Unexpected(s"Could not add @type to the payload '${value.json}'"))
    }
  }

  override def tags: Set[String] = Set("project")
}

object Projects {

  private[projects] implicit val logger: Logger = Logger[this.type]

  implicit val projectReferencePersistenceId = new PersistenceId[ProjectReference] {
    override def persistenceId(id: ProjectReference): String = id.value
  }

  /**
    * Constructs a new ''Projects'' instance that bundles operations that can be performed against projects using the
    * underlying persistence abstraction.
    *
    * @param organizations  organizations operation bundle
    * @param agg    the aggregate definition
    * @param F      a MonadError typeclass instance for ''F[_]''
    * @param config the project specific settings
    * @tparam F the monadic effect type
    */
  final def apply[F[_]](organizations: Organizations[F], agg: Agg[F], sparqlClient: SparqlClient[F])(
      implicit F: MonadError[F, Throwable],
      config: ProjectsConfig,
      validator: ShaclValidator[F]): Projects[F] = {
    implicit val logger: Logger = Logger[this.type]
    new Projects[F](organizations, agg, sparqlClient)
  }
}

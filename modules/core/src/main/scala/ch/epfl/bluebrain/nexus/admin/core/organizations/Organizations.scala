package ch.epfl.bluebrain.nexus.admin.core.organizations

import java.time.Clock

import cats.MonadError
import cats.syntax.all._
import ch.epfl.bluebrain.nexus.admin.core.Fault.{CommandRejected, Unexpected}
import ch.epfl.bluebrain.nexus.admin.core.config.AppConfig.{OrganizationsConfig, _}
import ch.epfl.bluebrain.nexus.admin.core.persistence.PersistenceId
import ch.epfl.bluebrain.nexus.admin.core.resources.ResourceRejection.{
  IllegalImportsViolation,
  MissingImportsViolation,
  ShapeConstraintViolations
}
import ch.epfl.bluebrain.nexus.admin.core.resources.Resources.Agg
import ch.epfl.bluebrain.nexus.admin.core.resources._
import ch.epfl.bluebrain.nexus.admin.core.types.Ref._
import ch.epfl.bluebrain.nexus.admin.ld.Const.{nxv, organizationContext, organizationSchema, rdf}
import ch.epfl.bluebrain.nexus.admin.ld.JsonLD
import ch.epfl.bluebrain.nexus.admin.ld.JsonLD._
import ch.epfl.bluebrain.nexus.admin.refined.organization.OrganizationReference
import ch.epfl.bluebrain.nexus.commons.shacl.validator.ShaclValidatorErr.{CouldNotFindImports, IllegalImportDefinition}
import ch.epfl.bluebrain.nexus.commons.shacl.validator.{ShaclSchema, ShaclValidator}
import ch.epfl.bluebrain.nexus.commons.sparql.client.SparqlClient
import io.circe.Json
import journal.Logger

class Organizations[F[_]](agg: Agg[F], sparqlClient: SparqlClient[F])(
    implicit
    F: MonadError[F, Throwable],
    logger: Logger,
    clock: Clock,
    validator: ShaclValidator[F],
    config: OrganizationsConfig,
    persistenceId: PersistenceId[OrganizationReference])
    extends Resources[F, OrganizationReference](agg, sparqlClient) {

  /**
    * Certain validation to take place during creation operations.
    *
    * @param id    the identifier of the resource
    * @param value the json payload of the resource
    * @return () or the appropriate rejection in the ''F'' context
    */
  override def validateCreate(id: OrganizationReference, value: Json): F[Unit] = validate(id, value)

  /**
    * Certain validation to take place during update operations.
    *
    * @param id    the identifier of the resource
    * @param value the json payload of the resource
    * @return () or the appropriate rejection in the ''F'' context
    */
  override def validateUpdate(id: OrganizationReference, value: Json): F[Unit] = validate(id, value)

  private def validate(id: OrganizationReference, value: JsonLD): F[Unit] = {
    value.appendContext(organizationContext).add(rdf.tpe, nxv.Organization).add(nxv.label, id.value).apply() match {
      case Some(merged) =>
        validator(ShaclSchema(organizationSchema), merged)
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

  override val tags: Set[String] = Set("organization")
}

object Organizations {

  implicit val orgReferencePersistenceId = new PersistenceId[OrganizationReference] {
    override def persistenceId(id: OrganizationReference): String = id.value
  }

  def apply[F[_]](agg: Agg[F], sparqlClient: SparqlClient[F])(implicit
                                                              F: MonadError[F, Throwable],
                                                              validator: ShaclValidator[F],
                                                              config: OrganizationsConfig): Organizations[F] = {

    implicit val logger: Logger = Logger[this.type]
    new Organizations[F](agg, sparqlClient)

  }

}

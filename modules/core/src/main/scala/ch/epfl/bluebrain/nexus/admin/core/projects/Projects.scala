package ch.epfl.bluebrain.nexus.admin.core.projects

import java.time.Clock

import cats.MonadError
import cats.syntax.all._
import ch.epfl.bluebrain.nexus.admin.core.config.AppConfig._
import ch.epfl.bluebrain.nexus.admin.core.organizations.Organizations
import ch.epfl.bluebrain.nexus.admin.core.persistence.PersistenceId
import ch.epfl.bluebrain.nexus.admin.core.resources.Resources.Agg
import ch.epfl.bluebrain.nexus.admin.core.resources._
import ch.epfl.bluebrain.nexus.admin.core.types.Ref._
import ch.epfl.bluebrain.nexus.admin.ld.Const._
import ch.epfl.bluebrain.nexus.admin.ld.{IdRef, JsonLD}
import ch.epfl.bluebrain.nexus.admin.refined.project._
import ch.epfl.bluebrain.nexus.commons.shacl.validator.ShaclValidator
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

  override def validateUnlocked(id: ProjectReference): F[Unit] = {
    for {
      _ <- super.validateUnlocked(id)
      _ <- organizations.validateUnlocked(id.organizationReference)
    } yield ()

  }
  override def validate(id: ProjectReference, value: JsonLD): F[Unit] =
    for {
      _ <- super.validate(id, value)
      _ <- organizations.validateUnlocked(id.organizationReference)
    } yield ()

  override val tags: Set[String] = Set("project")

  override val resourceType: IdRef = nxv.Project

  override val resourceSchema: Json = projectSchema
}

object Projects {

  private[projects] implicit val logger: Logger = Logger[this.type]

  implicit val projectReferencePersistenceId = new PersistenceId[ProjectReference] {
    override def persistenceId(id: ProjectReference): String = {
      id.value
    }
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

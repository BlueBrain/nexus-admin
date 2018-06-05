package ch.epfl.bluebrain.nexus.admin.core.organizations

import java.time.Clock

import cats.MonadError
import ch.epfl.bluebrain.nexus.admin.core.config.AppConfig.{OrganizationsConfig, _}
import ch.epfl.bluebrain.nexus.admin.core.persistence.PersistenceId
import ch.epfl.bluebrain.nexus.admin.core.resources.Resources.Agg
import ch.epfl.bluebrain.nexus.admin.core.resources._
import ch.epfl.bluebrain.nexus.admin.core.types.Ref._
import ch.epfl.bluebrain.nexus.admin.ld.Const.{nxv, organizationSchema}
import ch.epfl.bluebrain.nexus.admin.ld.IdRef
import ch.epfl.bluebrain.nexus.admin.refined.organization.OrganizationReference
import ch.epfl.bluebrain.nexus.commons.shacl.validator.ShaclValidator
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

  override val tags: Set[String] = Set("organization")

  override val resourceType: IdRef = nxv.Organization

  override val resourceSchema: Json = organizationSchema
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

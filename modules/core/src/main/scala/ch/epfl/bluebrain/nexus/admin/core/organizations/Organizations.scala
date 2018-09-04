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
import ch.epfl.bluebrain.nexus.commons.sparql.client.SparqlClient
import ch.epfl.bluebrain.nexus.rdf.Graph
import journal.Logger

class Organizations[F[_]](agg: Agg[F], sparqlClient: SparqlClient[F])(
    implicit
    F: MonadError[F, Throwable],
    logger: Logger,
    clock: Clock,
    config: OrganizationsConfig,
    persConfig: PersistenceConfig,
    persistenceId: PersistenceId[OrganizationReference])
    extends Resources[F, OrganizationReference](agg, sparqlClient) {

  override def label(id: OrganizationReference): String = id.value

  override val tags: Set[String] = Set("organization", persConfig.defaultTag)

  override val resourceType: IdRef = nxv.Organization

  override val resourceSchema: Graph = organizationSchema
}

object Organizations {

  implicit val orgReferencePersistenceId = new PersistenceId[OrganizationReference] {
    override def persistenceId(id: OrganizationReference): String = id.value
  }

  def apply[F[_]](agg: Agg[F], sparqlClient: SparqlClient[F])(implicit
                                                              F: MonadError[F, Throwable],
                                                              config: OrganizationsConfig,
                                                              persConfig: PersistenceConfig): Organizations[F] = {

    implicit val logger: Logger = Logger[this.type]
    new Organizations[F](agg, sparqlClient)

  }

}

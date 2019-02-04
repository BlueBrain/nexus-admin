package ch.epfl.bluebrain.nexus.admin.index

import java.util.UUID

import akka.actor.ActorSystem
import cats.Monad
import cats.effect.{Async, Timer}
import cats.implicits._
import ch.epfl.bluebrain.nexus.admin.config.Permissions.orgs
import ch.epfl.bluebrain.nexus.admin.index.Cache._
import ch.epfl.bluebrain.nexus.admin.organizations.{Organization, OrganizationResource}
import ch.epfl.bluebrain.nexus.commons.types.search.Pagination
import ch.epfl.bluebrain.nexus.commons.types.search.QueryResult.UnscoredQueryResult
import ch.epfl.bluebrain.nexus.commons.types.search.QueryResults.UnscoredQueryResults
import ch.epfl.bluebrain.nexus.iam.client.types.AccessControlLists
import ch.epfl.bluebrain.nexus.service.indexer.cache.{KeyValueStore, KeyValueStoreConfig}

/**
  * The organization cache backed by a KeyValueStore using akka Distributed Data
  *
  * @param store the underlying Distributed Data LWWMap store.
  * @tparam F the effect type ''F[_]''
  */
class OrganizationCache[F[_]](store: KeyValueStore[F, UUID, OrganizationResource])(implicit F: Monad[F])
    extends Cache[F, Organization](store) {

  private implicit val ordering: Ordering[OrganizationResource] = Ordering.by { org: OrganizationResource =>
    org.value.label
  }

  /**
    * Return the elements on the store within the ''pagination'' bounds which are accessible by the provided acls with the permission 'projects/read'.
    *
    * @param pagination the pagination
    */
  def list(pagination: Pagination)(implicit acls: AccessControlLists): F[UnscoredQueryResults[OrganizationResource]] =
    store.values.map { values =>
      val filtered = values.filter { org =>
        acls.exists(org.value.label, orgs.read)
      }
      val count  = filtered.size.toLong
      val result = filtered.toList.sorted.slice(pagination.from.toInt, (pagination.from + pagination.size).toInt)
      UnscoredQueryResults(count, result.map(UnscoredQueryResult(_)))
    }

  /**
    * Attempts to fetch the organization resource with the provided ''label''
    *
    * @param label the organization label
    */
  def getBy(label: String): F[Option[OrganizationResource]] =
    store.findValue(_.value.label == label)
}
object OrganizationCache {

  /**
    * Creates a new organization index.
    */
  def apply[F[_]: Timer](implicit as: ActorSystem, config: KeyValueStoreConfig, F: Async[F]): OrganizationCache[F] = {
    val function: (Long, OrganizationResource) => Long = { case (_, res) => res.rev }
    new OrganizationCache(KeyValueStore.distributed("organizations", function, mapError))(F)
  }
}

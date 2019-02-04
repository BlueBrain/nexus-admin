package ch.epfl.bluebrain.nexus.admin.index

import java.util.UUID

import akka.actor.ActorSystem
import cats.Monad
import cats.effect.{Async, Timer}
import cats.implicits._
import ch.epfl.bluebrain.nexus.admin.index.Cache._
import ch.epfl.bluebrain.nexus.admin.organizations.{Organization, OrganizationResource}
import ch.epfl.bluebrain.nexus.service.indexer.cache.{KeyValueStore, KeyValueStoreConfig}

/**
  * The organization cache backed by a KeyValueStore using akka Distributed Data
  *
  * @param store the underlying Distributed Data LWWMap store.
  * @tparam F the effect type ''F[_]''
  */
class OrganizationCache[F[_]](store: KeyValueStore[F, UUID, OrganizationResource])(implicit F: Monad[F])
    extends Cache[F, Organization](store) {

  override implicit val ordering: Ordering[OrganizationResource] = Ordering.by { org: OrganizationResource =>
    org.value.label
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

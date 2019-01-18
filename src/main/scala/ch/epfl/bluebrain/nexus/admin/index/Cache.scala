package ch.epfl.bluebrain.nexus.admin.index

import java.util.UUID

import akka.actor.ActorSystem
import cats.Monad
import cats.effect.{Async, Timer}
import cats.implicits._
import ch.epfl.bluebrain.nexus.admin.exceptions.AdminError
import ch.epfl.bluebrain.nexus.admin.exceptions.AdminError.InternalError
import ch.epfl.bluebrain.nexus.admin.types.ResourceF
import ch.epfl.bluebrain.nexus.commons.types.search.Pagination
import ch.epfl.bluebrain.nexus.commons.types.search.QueryResult.UnscoredQueryResult
import ch.epfl.bluebrain.nexus.commons.types.search.QueryResults.UnscoredQueryResults
import ch.epfl.bluebrain.nexus.service.indexer.cache.{KeyValueStore, KeyValueStoreConfig, OnKeyValueStoreChange}

abstract class Cache[F[_], V](val store: KeyValueStore[F, UUID, ResourceF[V]])(implicit F: Monad[F]) {

  implicit def ordering: Ordering[ResourceF[V]]

  /**
    * Return the elements on the store within the ''pagination'' bounds.
    *
    * @param pagination the pagination
    */
  def list(pagination: Pagination): F[UnscoredQueryResults[ResourceF[V]]] =
    store.values.map { values =>
      val count = values.size.toLong
      val list  = values.toList.sorted.slice(pagination.from.toInt, (pagination.from + pagination.size).toInt)
      UnscoredQueryResults(count, list.map(UnscoredQueryResult(_)))
    }

  /**
    * Attempts to fetch the resource with the provided ''id''
    *
    * @param id the resource id
    */
  def get(id: UUID): F[Option[ResourceF[V]]] =
    store.get(id)

  /**
    * Creates or replaces the resource with key ''id'' and value ''value''
    *
    * @param id    the resource key
    * @param value the resource value
    */
  def replace(id: UUID, value: ResourceF[V]): F[Unit] =
    store.put(id, value)
}

object Cache {

  private[index] def storeWrappedError[F[_]: Timer, V](
      name: String,
      f: V => Long)(implicit as: ActorSystem, config: KeyValueStoreConfig, F: Async[F]): KeyValueStore[F, UUID, V] =
    new KeyValueStore[F, UUID, V] {
      val underlying: KeyValueStore[F, UUID, V] = KeyValueStore.distributed(name, (_, resource) => f(resource))

      override def put(key: UUID, value: V): F[Unit] =
        underlying.put(key, value).recoverWith { case err => F.raiseError(InternalError(err.getMessage): AdminError) }
      override def entries: F[Map[UUID, V]] =
        underlying.entries.recoverWith { case err => F.raiseError(InternalError(err.getMessage): AdminError) }
      override def remove(key: UUID): F[Unit] =
        underlying.remove(key).recoverWith { case err => F.raiseError(InternalError(err.getMessage): AdminError) }
      override def subscribe(value: OnKeyValueStoreChange[UUID, V]): F[KeyValueStore.Subscription] =
        underlying.subscribe(value).recoverWith { case err => F.raiseError(InternalError(err.getMessage): AdminError) }
      override def unsubscribe(subscription: KeyValueStore.Subscription): F[Unit] =
        underlying.unsubscribe(subscription).recoverWith {
          case err => F.raiseError(InternalError(err.getMessage): AdminError)
        }
    }
}

package ch.epfl.bluebrain.nexus.admin.index

import java.util.UUID

import cats.Monad
import cats.implicits._
import ch.epfl.bluebrain.nexus.admin.exceptions.AdminError
import ch.epfl.bluebrain.nexus.admin.exceptions.AdminError.{InternalError, OperationTimedOut}
import ch.epfl.bluebrain.nexus.admin.types.ResourceF
import ch.epfl.bluebrain.nexus.commons.types.search.Pagination
import ch.epfl.bluebrain.nexus.commons.types.search.QueryResult.UnscoredQueryResult
import ch.epfl.bluebrain.nexus.commons.types.search.QueryResults.UnscoredQueryResults
import ch.epfl.bluebrain.nexus.service.indexer.cache.KeyValueStoreError.{
  DistributedDataError,
  ReadWriteConsistencyTimeout
}
import ch.epfl.bluebrain.nexus.service.indexer.cache.{KeyValueStore, KeyValueStoreError}

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

  private[index] def mapError(cacheError: KeyValueStoreError): AdminError =
    cacheError match {
      case e: ReadWriteConsistencyTimeout =>
        OperationTimedOut(s"Timeout while interacting with the cache due to '${e.timeout}'")
      case e: DistributedDataError => InternalError(e.reason)
    }
}

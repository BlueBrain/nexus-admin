package ch.epfl.bluebrain.nexus.admin.index

import java.util.UUID

import cats.effect.Async
import cats.{Monad, MonadError}
import ch.epfl.bluebrain.nexus.admin.exceptions.AdminError
import ch.epfl.bluebrain.nexus.admin.exceptions.AdminError.{InternalError, OperationTimedOut}
import ch.epfl.bluebrain.nexus.admin.types.ResourceF
import ch.epfl.bluebrain.nexus.commons.cache.KeyValueStoreError._
import ch.epfl.bluebrain.nexus.commons.cache.{KeyValueStore, KeyValueStoreError}
import ch.epfl.bluebrain.nexus.iam.client.types.{AccessControlLists, Permission}
import ch.epfl.bluebrain.nexus.rdf.Iri.Path._

abstract class Cache[F[_], V](val store: KeyValueStore[F, UUID, ResourceF[V]])(implicit F: Monad[F]) {

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

  private[index] final implicit class AclsSyntax(private val acls: AccessControlLists) extends AnyVal {

    /**
      * Checks if on the list of ACLs there are some which contains the provided ''permission'' on either the /
      * or the provided ''organization'' or the provided ''project''
      *
      * @param organization the organization label
      * @param project     the project label
      * @param permission  the permissions to filter
      * @return true if the conditions are met, false otherwise
      */
    def exists(organization: String, project: String, permission: Permission): Boolean =
      acls.value.exists {
        case (path, v) =>
          (path == / || path == Segment(organization, /) || path == organization / project) &&
            v.value.permissions.contains(permission)
      }

    /**
      * Checks if on the list of ACLs there are some which contains the provided ''permission'' on either the /
      * or the provided ''organization''
      *
      * @param organization the organization label
      * @param permission  the permissions to filter
      * @return true if the conditions are met, false otherwise
      */
    def exists(organization: String, permission: Permission): Boolean =
      acls.value.exists {
        case (path, v) => (path == / || path == Segment(organization, /)) && v.value.permissions.contains(permission)
      }
  }

  private[index] implicit def monadError[F[_]](implicit F: Async[F]): MonadError[F, AdminError] =
    new MonadError[F, AdminError] {
      override def handleErrorWith[A](fa: F[A])(f: AdminError => F[A]): F[A] = F.recoverWith(fa) {
        case t: AdminError => f(t)
      }
      override def raiseError[A](e: AdminError): F[A]                  = F.raiseError(e)
      override def pure[A](x: A): F[A]                                 = F.pure(x)
      override def flatMap[A, B](fa: F[A])(f: A => F[B]): F[B]         = F.flatMap(fa)(f)
      override def tailRecM[A, B](a: A)(f: A => F[Either[A, B]]): F[B] = F.tailRecM(a)(f)
    }

  private[index] def mapError(cacheError: KeyValueStoreError): AdminError =
    cacheError match {
      case e: ReadWriteConsistencyTimeout =>
        OperationTimedOut(s"Timeout while interacting with the cache due to '${e.timeout}'")
      case e: DistributedDataError => InternalError(e.reason)
    }
}

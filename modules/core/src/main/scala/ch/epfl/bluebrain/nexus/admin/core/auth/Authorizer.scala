package ch.epfl.bluebrain.nexus.admin.core.auth

import akka.actor.ActorSystem
import akka.http.scaladsl.model.headers.OAuth2BearerToken
import cats.instances.future._
import cats.syntax.all._
import cats.{MonadError, Show}
import ch.epfl.bluebrain.nexus.admin.core.CallerCtx
import ch.epfl.bluebrain.nexus.admin.core.auth.Authorizer.AuthorizeError.UnmatchedPermission
import ch.epfl.bluebrain.nexus.admin.core.auth.Authorizer.{AuthorizeError, Key}
import ch.epfl.bluebrain.nexus.commons.iam.IamClient
import ch.epfl.bluebrain.nexus.commons.iam.acls.{FullAccessControlList, Path, Permission, Permissions}
import ch.epfl.bluebrain.nexus.commons.types.Err
import ch.epfl.bluebrain.nexus.commons.types.HttpRejection.UnauthorizedAccess
import ch.epfl.bluebrain.nexus.sourcing.akka.cache.ShardedCache.CacheSettings
import ch.epfl.bluebrain.nexus.sourcing.akka.cache.{Cache, ShardedCache}

import scala.concurrent.Future

/**
  * Operations to authorize ''resources'' against the IAM service. The requests to IAM are limited due to cache mechanism
  *
  * @param client the underlying IAM client
  * @param cache  the implementation chosen for the [[Cache]] trait
  * @param F      a MonadError typeclass instance for ''F[_]''
  * @tparam F the monadic effect type
  */
class Authorizer[F[_]](client: IamClient[F], cache: Cache[F, Key, FullAccessControlList])(
    implicit F: MonadError[F, Throwable]) {

  /**
    * Retrieve the ACLs for the provided ''resource'' path on the provided ''caller''.
    * This request will be cached for performance.
    *
    * @param resource the resource path from where to retrieve the ACLs
    * @param ctx      provides the information about the ''caller''
    */
  def acls(resource: Path)(implicit ctx: CallerCtx): F[FullAccessControlList] =
    cache.get(resource) flatMap {
      case Some(acl) => F.pure(acl)
      case None =>
        for {
          acl <- client.getAcls(resource, self = true, parents = true)
          _   <- cache.put(resource, acl)
        } yield acl
    } recoverWith {
      case UnauthorizedAccess => F.raiseError(AuthorizeError.UnauthorizedAccess)
      case err                => F.raiseError(AuthorizeError.Unexpected(err))
    }

  /**
    * Checks if a ''caller'' has the provided permission ''perm'' for the resource path ''resource''
    *
    * @param resource the resource path from where to check the ACLs
    * @param perm     the permission to be checked
    * @param ctx      provides the information about the ''caller''
    */
  def validateAccess(resource: Path, perm: Permission)(implicit ctx: CallerCtx): F[Unit] =
    acls(resource).flatMap { acl =>
      if (acl.hasAnyPermission(Permissions(perm))) F.pure(())
      else F.raiseError(UnmatchedPermission(perm))
    }

  private implicit def toToken(implicit ctx: CallerCtx): Option[OAuth2BearerToken] = ctx.caller.credentials
  private implicit def toKey(resource: Path)(implicit tokenOpt: Option[OAuth2BearerToken]): Key =
    Key(resource, tokenOpt.map(_.token).getOrElse(""))
}

object Authorizer {

  /**
    * Constructs an ''Authorizer''
    *
    * @param client the underlying IAM client
    * @param cache  the implementation chosen for the [[Cache]] trait
    * @param F      a MonadError typeclass instance for ''F[_]''
    * @tparam F the monadic effect type
    */
  final def apply[F[_]](client: IamClient[F], cache: Cache[F, Key, FullAccessControlList])(
      implicit F: MonadError[F, Throwable]): Authorizer[F] = new Authorizer(client, cache)

  /**
    * Constructs an ''Authorizer'' wrapped in a [[Future]]
    *
    * @param client    the underlying IAM client
    * @param cacheConf the general settings for the akka based cache implementation
    * @param as        the [[ActorSystem]]
    */
  final def apply(client: IamClient[Future])(implicit cacheConf: CacheSettings, as: ActorSystem): Authorizer[Future] = {
    import as.dispatcher
    val cache = ShardedCache[Key, FullAccessControlList]("acls", cacheConf)
    new Authorizer(client, cache)
  }

  final private[auth] case class Key(path: Path, token: String)
  private[auth] implicit val keyShow: Show[Key] = Show.show { case Key(path, token) => s"${path.show}-$token" }

  @SuppressWarnings(Array("IncorrectlyNamedExceptions"))
  /**
    * Base enumeration type for AuthorizeError classes.
    */
  sealed abstract class AuthorizeError(reason: String) extends Err(reason)

  @SuppressWarnings(Array("IncorrectlyNamedExceptions"))
  object AuthorizeError {

    /**
      * Signals that the provided permission was not not found on the ACLs
      *
      * @param perm the permission to be checked
      */
    final case class UnmatchedPermission(perm: Permission) extends AuthorizeError(s"UnmatchedPermission $perm")

    /**
      * Singals that the ''caller'' does not have access to perform ACLs check
      */
    final case object UnauthorizedAccess extends AuthorizeError("UnauthorizedAccess")

    /**
      * Signals an unexpected error
      *
      * @param th the underying [[Throwable]]
      */
    final case class Unexpected(th: Throwable) extends AuthorizeError(th.getMessage)

  }
}

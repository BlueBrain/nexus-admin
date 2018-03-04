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

class Authorizer[F[_]](client: IamClient[F], cache: Cache[F, Key, FullAccessControlList])(
    implicit F: MonadError[F, Throwable]) {

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

  final def apply[F[_]](client: IamClient[F], cache: Cache[F, Key, FullAccessControlList])(
      implicit F: MonadError[F, Throwable]): Authorizer[F] = new Authorizer(client, cache)

  final def apply(client: IamClient[Future])(implicit cacheConf: CacheSettings, as: ActorSystem): Authorizer[Future] = {
    import as.dispatcher
    val cache = ShardedCache[Key, FullAccessControlList]("acls", cacheConf)
    new Authorizer(client, cache)
  }

  private[auth] case class Key(path: Path, token: String)
  private[auth] implicit val keyShow: Show[Key] = Show.show { case Key(path, token) => s"${path.show}-$token" }

  @SuppressWarnings(Array("IncorrectlyNamedExceptions"))
  sealed abstract class AuthorizeError(reason: String) extends Err(reason)

  @SuppressWarnings(Array("IncorrectlyNamedExceptions"))
  object AuthorizeError {

    final case class UnmatchedPermission(perm: Permission) extends AuthorizeError(s"UnmatchedPermission $perm")

    final case object UnauthorizedAccess extends AuthorizeError("UnauthorizedAccess")

    final case class Unexpected(th: Throwable) extends AuthorizeError(th.getMessage)

  }
}

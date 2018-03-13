package ch.epfl.bluebrain.nexus.admin.core.directives

import akka.http.javadsl.server.CustomRejection
import akka.http.scaladsl.model.headers.OAuth2BearerToken
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.directives.FutureDirectives.onComplete
import akka.http.scaladsl.server.directives.RouteDirectives.reject
import akka.http.scaladsl.server.{AuthorizationFailedRejection, _}
import ch.epfl.bluebrain.nexus.admin.core.directives.AuthDirectives._
import ch.epfl.bluebrain.nexus.admin.core.rejections.CommonRejections
import ch.epfl.bluebrain.nexus.admin.core.rejections.CommonRejections.DownstreamServiceError
import ch.epfl.bluebrain.nexus.commons.iam.IamClient
import ch.epfl.bluebrain.nexus.commons.iam.acls.{Path, Permissions}
import ch.epfl.bluebrain.nexus.commons.iam.identity.Caller
import ch.epfl.bluebrain.nexus.commons.types.HttpRejection.UnauthorizedAccess
import eu.timepit.refined.api.RefType.applyRef
import eu.timepit.refined.api.{Refined, Validate}

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

trait AuthDirectives {

  /**
    * Checks if the caller associated with ''cred'' has permissions constrained by the type ''Perm'' on the path ''resource''.
    *
    * @param resource the path which the caller wants to access
    * @tparam A the restricted subset of [[Permissions]]
    */
  def authorizePath[A](resource: Path)(implicit client: IamClient[Future],
                                   cred: Option[OAuth2BearerToken],
                                   V: Validate[Permissions, A]): Directive1[Permissions Refined A] =
    onComplete(client.getAcls(resource, self = true, parents = true)).flatMap {
      case Success(acl) =>
        applyRef[Permissions Refined A](acl.permissions) match {
          case Right(instance) => provide(instance)
          case Left(_)         => reject(AuthorizationFailedRejection)
        }
      case Failure(UnauthorizedAccess) => reject(AuthorizationFailedRejection)
      case Failure(err)                => reject(authorizationRejection(err))
    }

  /**
    * Checks if the caller associated with ''cred'' has permissions constrained by the type ''Perm'' on the path ''resource''.
    *
    * @param resource the resource id which the caller wants to access
    * @tparam A the restricted subset of [[Permissions]]
    */
  def authorizePath[A](resource: String)(implicit client: IamClient[Future],
                                     cred: Option[OAuth2BearerToken],
                                     V: Validate[Permissions, A]): Directive1[Permissions Refined A] =
    authorizePath[A](Path(resource))

  /**
    * Authenticates the requested with the provided ''cred'' and returns the ''caller''
    *
    * @return the [[Caller]]
    */
  def authCaller(implicit iamClient: IamClient[Future], cred: Option[OAuth2BearerToken]): Directive1[Caller] =
    onComplete(iamClient.getCaller(cred, filterGroups = true)).flatMap {
      case Success(caller)             => provide(caller)
      case Failure(UnauthorizedAccess) => reject(AuthorizationFailedRejection)
      case Failure(err)                => reject(authorizationRejection(err))
    }

}

object AuthDirectives extends AuthDirectives {

  /**
    * Signals that the authentication was rejected with an unexpected error.
    *
    * @param err the [[ch.epfl.bluebrain.nexus.admin.core.rejections.CommonRejections]]
    */
  final case class CustomAuthRejection(err: CommonRejections) extends CustomRejection

  private[directives] def authorizationRejection(err: Throwable) =
    CustomAuthRejection(
      DownstreamServiceError(Try(err.getMessage).getOrElse("error while authenticating on the downstream service")))
}

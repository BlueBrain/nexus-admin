package ch.epfl.bluebrain.nexus.admin.core.directives

import akka.http.javadsl.server.CustomRejection
import akka.http.scaladsl.model.headers.OAuth2BearerToken
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.directives.FutureDirectives.onComplete
import akka.http.scaladsl.server.directives.RouteDirectives.reject
import akka.http.scaladsl.server.{AuthorizationFailedRejection, _}
import ch.epfl.bluebrain.nexus.admin.core.directives.AuthenticateDirectives._
import ch.epfl.bluebrain.nexus.admin.core.rejections.CommonRejections
import ch.epfl.bluebrain.nexus.admin.core.rejections.CommonRejections.DownstreamServiceError
import ch.epfl.bluebrain.nexus.commons.iam.IamClient
import ch.epfl.bluebrain.nexus.commons.iam.identity.Caller
import ch.epfl.bluebrain.nexus.commons.types.HttpRejection.UnauthorizedAccess

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

trait AuthenticateDirectives {

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

object AuthenticateDirectives extends AuthenticateDirectives {

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

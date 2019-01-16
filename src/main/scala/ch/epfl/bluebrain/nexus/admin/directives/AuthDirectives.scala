package ch.epfl.bluebrain.nexus.admin.directives

import akka.http.javadsl.server.CustomRejection
import akka.http.scaladsl.model.headers.{HttpChallenges, OAuth2BearerToken}
import akka.http.scaladsl.server.AuthenticationFailedRejection.CredentialsRejected
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import akka.http.scaladsl.server.directives.FutureDirectives.onComplete
import akka.http.scaladsl.server.directives.RouteDirectives.reject
import ch.epfl.bluebrain.nexus.admin.CommonRejection
import ch.epfl.bluebrain.nexus.admin.CommonRejection.DownstreamServiceError
import ch.epfl.bluebrain.nexus.admin.directives.AuthDirectives._
import ch.epfl.bluebrain.nexus.commons.types.HttpRejection.UnauthorizedAccess
import ch.epfl.bluebrain.nexus.iam.client.IamClient
import ch.epfl.bluebrain.nexus.iam.client.types.Identity.Subject
import ch.epfl.bluebrain.nexus.iam.client.types.{AuthToken, Permission}
import ch.epfl.bluebrain.nexus.rdf.Iri.Path
import monix.eval.Task
import monix.execution.Scheduler

import scala.util.{Failure, Success}

/**
  * Akka HTTP directives that wrap authentication and authorization calls.
  *
  * @param iamClient the underlying IAM client
  */
abstract class AuthDirectives(iamClient: IamClient[Task])(implicit s: Scheduler) {

  /**
    * Checks if the caller has permissions on a provided ''resource''.
    *
    * @return forwards the provided resource [[Path]] if the caller has access.
    */
  def authorizeOn(resource: Path, permission: Permission)(implicit cred: Option[AuthToken]): Directive0 =
    onComplete(iamClient.authorizeOn(resource, permission).runToFuture).flatMap {
      case Success(_) => pass
      // TODO: change this to Forbidden after adding it to common types and iam client
      case Failure(UnauthorizedAccess) => reject(AuthorizationFailedRejection)
      case Failure(err)                => reject(authorizationRejection(err))
    }

  /**
    * Authenticates the request with the provided credentials.
    *
    * @return the [[Subject]] of the caller
    */
  def authCaller(implicit cred: Option[AuthToken]): Directive1[Subject] =
    onComplete(iamClient.identities.runToFuture).flatMap {
      case Success(caller) => provide(caller.subject)
      case Failure(UnauthorizedAccess) =>
        reject(AuthenticationFailedRejection(CredentialsRejected, HttpChallenges.oAuth2("*")))
      case Failure(err) => reject(authorizationRejection(err))
    }

  /**
    * Attempts to extract an [[AuthToken]] from the http headers.
    *
    * @return an optional token
    */
  def extractToken: Directive1[Option[AuthToken]] =
    extractCredentials.flatMap {
      case Some(OAuth2BearerToken(value)) => provide(Some(AuthToken(value)))
      case Some(_)                        => reject(AuthorizationFailedRejection)
      case _                              => provide(None)
    }

}

object AuthDirectives {

  /**
    * Signals that the authentication was rejected with an unexpected error.
    *
    * @param err the [[CommonRejection]]
    */
  final case class CustomAuthRejection(err: CommonRejection) extends CustomRejection

  private def authorizationRejection(err: Throwable) =
    CustomAuthRejection(
      DownstreamServiceError(Option(err.getMessage).getOrElse("error while authenticating on the downstream service")))
}

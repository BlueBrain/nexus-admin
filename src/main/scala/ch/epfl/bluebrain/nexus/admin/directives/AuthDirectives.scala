package ch.epfl.bluebrain.nexus.admin.directives

import akka.http.scaladsl.model.headers.{HttpChallenges, OAuth2BearerToken}
import akka.http.scaladsl.server.AuthenticationFailedRejection.CredentialsRejected
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.directives.FutureDirectives.onComplete
import akka.http.scaladsl.server.directives.RouteDirectives.reject
import akka.http.scaladsl.server._
import ch.epfl.bluebrain.nexus.admin.exceptions.AdminError.InternalError
import ch.epfl.bluebrain.nexus.iam.client.types.Identity.Subject
import ch.epfl.bluebrain.nexus.iam.client.types.{AuthToken, Permission}
import ch.epfl.bluebrain.nexus.iam.client.{IamClient, IamClientError}
import ch.epfl.bluebrain.nexus.rdf.Iri.Path
import journal.Logger
import monix.eval.Task
import monix.execution.Scheduler

import scala.util.{Failure, Success}

/**
  * Akka HTTP directives that wrap authentication and authorization calls.
  *
  * @param iamClient the underlying IAM client
  */
abstract class AuthDirectives(iamClient: IamClient[Task])(implicit s: Scheduler) {

  private val authRejection = AuthenticationFailedRejection(CredentialsRejected, HttpChallenges.oAuth2("*"))
  private val logger        = Logger[this.type]

  /**
    * Checks if the caller has permissions on a provided ''resource''.
    *
    * @return forwards the provided resource [[Path]] if the caller has access.
    */
  def authorizeOn(resource: Path, permission: Permission)(implicit cred: Option[AuthToken]): Directive0 =
    onComplete(iamClient.hasPermission(resource, permission).runToFuture).flatMap {
      case Success(true)                           => pass
      case Success(false)                          => reject(AuthorizationFailedRejection)
      case Failure(_: IamClientError.Unauthorized) => reject(authRejection)
      case Failure(_: IamClientError.Forbidden)    => reject(AuthorizationFailedRejection)
      case Failure(err: IamClientError.UnmarshallingError[_]) =>
        val message = "Unmarshalling error when trying to check for permissions"
        logger.error(message, err)
        failWith(InternalError(message))
      case Failure(err: IamClientError.UnknownError) =>
        val message = "Unknown error when trying to check for permissions"
        logger.error(message, err)
        failWith(InternalError(message))
      case Failure(err) =>
        val message = "Unknown error when trying to check for permissions"
        logger.error(message, err)
        failWith(InternalError(message))
    }

  /**
    * Authenticates the request with the provided credentials.
    *
    * @return the [[Subject]] of the caller
    */
  def extractSubject(implicit cred: Option[AuthToken]): Directive1[Subject] =
    onComplete(iamClient.identities.runToFuture).flatMap {
      case Success(caller)                         => provide(caller.subject)
      case Failure(_: IamClientError.Unauthorized) => reject(authRejection)
      case Failure(_: IamClientError.Forbidden)    => reject(AuthorizationFailedRejection)
      case Failure(err: IamClientError.UnmarshallingError[_]) =>
        val message = "Unmarshalling error when trying to extract the subject"
        logger.error(message, err)
        failWith(InternalError(message))
      case Failure(err: IamClientError.UnknownError) =>
        val message = "Unknown error when trying to extract the subject"
        logger.error(message, err)
        failWith(InternalError(message))
      case Failure(err) =>
        val message = "Unknown error when trying to extract the subject"
        logger.error(message, err)
        failWith(InternalError(message))
    }

  /**
    * Attempts to extract an [[AuthToken]] from the http headers.
    *
    * @return an optional token
    */
  def extractToken: Directive1[Option[AuthToken]] =
    extractCredentials.flatMap {
      case Some(OAuth2BearerToken(value)) => provide(Some(AuthToken(value)))
      case Some(_)                        => reject(authRejection)
      case _                              => provide(None)
    }
}

package ch.epfl.bluebrain.nexus.admin.directives

import akka.http.javadsl.server.CustomRejection
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.directives.FutureDirectives.onComplete
import akka.http.scaladsl.server.directives.RouteDirectives.reject
import akka.http.scaladsl.server.{AuthorizationFailedRejection, _}
import ch.epfl.bluebrain.nexus.admin.CommonRejection
import ch.epfl.bluebrain.nexus.admin.CommonRejection.DownstreamServiceError
import ch.epfl.bluebrain.nexus.commons.types.HttpRejection.UnauthorizedAccess
import ch.epfl.bluebrain.nexus.commons.types.identity.Identity
import ch.epfl.bluebrain.nexus.commons.types.identity.Identity.Anonymous
import ch.epfl.bluebrain.nexus.iam.client.IamClient
import ch.epfl.bluebrain.nexus.iam.client.types.AuthToken
import ch.epfl.bluebrain.nexus.rdf.Iri.Path
import monix.eval.Task
import monix.execution.Scheduler

import scala.util.{Failure, Success, Try}

/**
  * Akka HTTP directives that wrap authentication and authorization calls.
  *
  * @param iamClient the underlying IAM client
  */
abstract class AuthDirectives(iamClient: IamClient[Task])(implicit s: Scheduler) {

  /**
    * Checks if the caller has permissions on a provided ''resource''.
    *
    * @return the provided resource [[Path]] if the caller has access.
    */
  def authorizeOn(resource: Path)(implicit cred: Option[AuthToken]): Directive1[Path] = {
    // TODO: call iamClient.authorizeOn
    val _ = cred.isDefined
    provide(resource)
  }

  /**
    * Authenticates the request with the provided credentials.
    *
    * @return the [[Identity]] of the caller
    */
  def authCaller(implicit cred: Option[AuthToken]): Directive1[Identity] =
    onComplete(iamClient.getCaller(filterGroups = true).runToFuture).flatMap {
      case Success(_)             => provide(Anonymous()) // FIXME
      case Failure(UnauthorizedAccess) => reject(AuthorizationFailedRejection)
      case Failure(err)                => reject(authorizationRejection(err))
    }

  /**
    * Signals that the authentication was rejected with an unexpected error.
    *
    * @param err the [[CommonRejection]]
    */
  case class CustomAuthRejection(err: CommonRejection) extends CustomRejection

  private def authorizationRejection(err: Throwable) =
    CustomAuthRejection(
      DownstreamServiceError(Try(err.getMessage).getOrElse("error while authenticating on the downstream service")))
}

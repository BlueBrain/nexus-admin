package ch.epfl.bluebrain.nexus.admin.routes

import akka.http.scaladsl.model.headers.OAuth2BearerToken
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.directives.RouteDirectives.reject
import akka.http.scaladsl.server.{AuthorizationFailedRejection, Route}
import ch.epfl.bluebrain.nexus.admin.config.AppConfig.HttpConfig
import ch.epfl.bluebrain.nexus.iam.client.types.AuthToken

/**
  * Basic template to consume routes and forward auth tokens.
  */
trait CombinedRoutes {

  /**
    * To be overriden by the concrete routes implementation.
    *
    * @return combined routes defined by the instance.
    */
  protected def combined(implicit credentials: Option[AuthToken]): Route

  /**
    * Wraps combined routes in exception and rejection handlers.
    *
    * @param initialPrefix the initial prefix to be consumed
    */
  def combinedRoutesFor(initialPrefix: String)(implicit hc: HttpConfig): Route =
    handleExceptions(ExceptionHandling.handler) {
      handleRejections(RejectionHandling.handler) {
        pathPrefix(hc.prefix / initialPrefix) {
          extractCredentials {
            case Some(OAuth2BearerToken(value)) => combined(Some(AuthToken(value)))
            case Some(_)                        => reject(AuthorizationFailedRejection)
            case _                              => combined(None)
          }
        }
      }
    }
}

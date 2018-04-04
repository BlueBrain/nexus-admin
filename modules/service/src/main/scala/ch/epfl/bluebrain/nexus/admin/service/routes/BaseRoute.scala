package ch.epfl.bluebrain.nexus.admin.service.routes

import akka.http.scaladsl.model.headers.OAuth2BearerToken
import akka.http.scaladsl.server.Directives.{extractCredentials, handleExceptions, handleRejections, pathPrefix}
import akka.http.scaladsl.server.RouteConcatenation._
import akka.http.scaladsl.server.directives.RouteDirectives.reject
import akka.http.scaladsl.server.{AuthorizationFailedRejection, Route}
import ch.epfl.bluebrain.nexus.admin.core.config.AppConfig.PrefixesConfig
import ch.epfl.bluebrain.nexus.admin.service.handlers.{ExceptionHandling, RejectionHandling}
import ch.epfl.bluebrain.nexus.commons.http.JsonLdCirceSupport.OrderedKeys

/**
  * Provides with a basic template to consume routes, fetching the [[OAuth2BearerToken]] and apssing it along.
  * It splits the routes into different categories: ''readRoutes'', ''writeRoutes'' and ''searchRoutes''.
  *
  * @param prefixes         the implicitly available prefixes for JSON-LD context generation
  * @param errorOrderedKeys the implicitly available JSON keys ordering on response payload
  */
abstract class BaseRoute(implicit prefixes: PrefixesConfig, errorOrderedKeys: OrderedKeys) {

  protected def readRoutes(implicit credentials: Option[OAuth2BearerToken]): Route

  protected def writeRoutes(implicit credentials: Option[OAuth2BearerToken]): Route

  /**
    * Combining ''writeRoutes'' with ''readRoutes'' and ''searchRoutes''
    * and add rejection and exception handling to it.
    *
    * @param initialPrefix the initial prefix to be consumed
    */
  def combinedRoutesFor(initialPrefix: String): Route =
    handleExceptions(ExceptionHandling.exceptionHandler(prefixes.errorContext, errorOrderedKeys)) {
      handleRejections(RejectionHandling.rejectionHandler(prefixes.errorContext, errorOrderedKeys)) {
        pathPrefix(initialPrefix) {
          extractCredentials {
            case Some(c: OAuth2BearerToken) => combine(Some(c))
            case Some(_)                    => reject(AuthorizationFailedRejection)
            case _                          => combine(None)
          }
        }
      }
    }

  private def combine(cred: Option[OAuth2BearerToken]) =
    readRoutes(cred) ~ writeRoutes(cred)

}

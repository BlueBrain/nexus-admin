package ch.epfl.bluebrain.nexus.admin.service.routes

import akka.http.scaladsl.model.headers.OAuth2BearerToken
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.RouteConcatenation._
import ch.epfl.bluebrain.nexus.admin.core.config.AppConfig.PrefixesConfig
import ch.epfl.bluebrain.nexus.commons.http.JsonLdCirceSupport.OrderedKeys

/**
  * Provides with a basic template to consume routes, fetching the [[OAuth2BearerToken]] and passing it along.
  * It splits the routes into different categories: ''readRoutes'', ''writeRoutes'' and ''searchRoutes''.
  *
  * @param prefixes         the implicitly available prefixes for JSON-LD context generation
  * @param errorOrderedKeys the implicitly available JSON keys ordering on response payload
  */
abstract class BaseRouteSplit(implicit prefixes: PrefixesConfig, errorOrderedKeys: OrderedKeys) extends BaseRoute {

  protected def readRoutes(implicit credentials: Option[OAuth2BearerToken]): Route

  protected def writeRoutes(implicit credentials: Option[OAuth2BearerToken]): Route

  override protected def combined(implicit cred: Option[OAuth2BearerToken]): Route =
    readRoutes(cred) ~ writeRoutes(cred)

}

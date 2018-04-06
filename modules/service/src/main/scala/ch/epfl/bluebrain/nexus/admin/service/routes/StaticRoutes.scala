package ch.epfl.bluebrain.nexus.admin.service.routes

import akka.http.scaladsl.model.{StatusCodes, Uri}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import ch.epfl.bluebrain.nexus.admin.core.config.AppConfig.{DescriptionConfig, HttpConfig}
import ch.epfl.bluebrain.nexus.admin.service.types.ServiceDescription
import ch.epfl.bluebrain.nexus.commons.iam.acls.Path
import ch.epfl.bluebrain.nexus.commons.iam.acls.Path._
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe.Printer
import io.circe.generic.auto._
import kamon.akka.http.KamonTraceDirectives.operationName

/**
  * Akka HTTP route definition for things that can be considered static, namely:
  * <ul>
  * <li>The service description</li>
  * <li>The service specification</li>
  * <li>The service specification browser</li>
  * </ul>
  */
class StaticRoutes(serviceDescription: ServiceDescription, uri: Uri, prefix: String) {

  private implicit val printer = Printer.noSpaces.copy(dropNullValues = true)

  private def serviceDescriptionRoute: Route =
    (get & pathEndOrSingleSlash) {
      operationName("serviceDescription") {
        complete(serviceDescription)
      }
    }

  private def docsRoute =
    pathPrefix("docs") {
      operationName("getDocumentation") {
        pathPrefix("admin") {
          pathEndOrSingleSlash {
            redirectToTrailingSlashIfMissing(StatusCodes.MovedPermanently) {
              getFromResource("docs/index.html")
            }
          } ~
            getFromResourceDirectory("docs")
        }
      }
    }

  def routes: Route = serviceDescriptionRoute ~ docsRoute

  private implicit class UriSyntax(u: Uri) {
    def append(path: Path): Uri = u.copy(path = (u.path: Path) ++ path)
  }

}

object StaticRoutes {

  /**
    * Default factory method for building [[ch.epfl.bluebrain.nexus.admin.service.routes.StaticRoutes]] instances.
    *
    * @param httpConfig the implicitly available http service configuration
    * @param descConfig the implicitly available description service configuration
    * @return a new [[ch.epfl.bluebrain.nexus.admin.service.routes.StaticRoutes]] instance
    */
  def apply()(implicit httpConfig: HttpConfig, descConfig: DescriptionConfig): StaticRoutes =
    new StaticRoutes(ServiceDescription(descConfig.name, descConfig.version), httpConfig.apiUri, httpConfig.prefix)

}

package ch.epfl.bluebrain.nexus.admin.directives
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives.{complete, get}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.ScalatestRouteTest
import ch.epfl.bluebrain.nexus.admin.config.AppConfig.HttpConfig
import ch.epfl.bluebrain.nexus.admin.config.{AppConfig, Settings}
import ch.epfl.bluebrain.nexus.admin.routes.Routes
import ch.epfl.bluebrain.nexus.commons.search.FromPagination
import org.mockito.IdiomaticMockito
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{EitherValues, Matchers, WordSpecLike}

class QueryDirectivesSpec
    extends WordSpecLike
    with ScalatestRouteTest
    with Matchers
    with ScalaFutures
    with EitherValues
    with IdiomaticMockito {

  private val appConfig: AppConfig      = Settings(system).appConfig
  private implicit val http: HttpConfig = appConfig.http

  private def routes(inner: Route): Route =
    Routes.wrap(inner)

  "Query directives" should {
    "handle pagination" in {
      def paginated(from: Int, size: Int) =
        Routes.wrap(
          (get & QueryDirectives.paginated(AppConfig.PaginationConfig(50, 100))) { pagination =>
            pagination shouldEqual FromPagination(from, size)
            complete(StatusCodes.Accepted)
          }
        )

      Get("/") ~> routes(paginated(0, 50)) ~> check {
        status shouldEqual StatusCodes.Accepted
      }
      Get("/?size=42") ~> routes(paginated(0, 42)) ~> check {
        status shouldEqual StatusCodes.Accepted
      }
      Get("/?from=1") ~> routes(paginated(1, 50)) ~> check {
        status shouldEqual StatusCodes.Accepted
      }
      Get("/?from=1&size=42") ~> routes(paginated(1, 42)) ~> check {
        status shouldEqual StatusCodes.Accepted
      }
      Get("/?from=1&size=-42") ~> routes(paginated(1, 0)) ~> check {
        status shouldEqual StatusCodes.Accepted
      }
      Get("/?from=1&size=500") ~> routes(paginated(1, 100)) ~> check {
        status shouldEqual StatusCodes.Accepted
      }
    }
  }
}

package ch.epfl.bluebrain.nexus.admin.directives
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives.{complete, get, handleExceptions, handleRejections}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.ScalatestRouteTest
import ch.epfl.bluebrain.nexus.admin.CommonRejection.IllegalParameter
import ch.epfl.bluebrain.nexus.admin.Error
import ch.epfl.bluebrain.nexus.admin.Error._
import ch.epfl.bluebrain.nexus.admin.config.AppConfig
import ch.epfl.bluebrain.nexus.admin.config.Contexts.errorCtxUri
import ch.epfl.bluebrain.nexus.admin.marshallers.instances._
import ch.epfl.bluebrain.nexus.admin.routes.{ExceptionHandling, RejectionHandling}
import ch.epfl.bluebrain.nexus.commons.types.search.Pagination
import ch.epfl.bluebrain.nexus.rdf.Iri.Path
import org.mockito.integrations.scalatest.IdiomaticMockitoFixture
import org.scalatest.{EitherValues, Matchers, WordSpecLike}
import org.scalatest.concurrent.ScalaFutures

class QueryDirectivesSpec
    extends WordSpecLike
    with ScalatestRouteTest
    with Matchers
    with ScalaFutures
    with EitherValues
    with IdiomaticMockitoFixture {

  private def routes(inner: Route): Route =
    handleExceptions(ExceptionHandling.handler) {
      handleRejections(RejectionHandling.handler) {
        inner
      }
    }

  "Query directives" should {
    "handle pagination" in {
      def paginated(from: Long, size: Int) = (get & QueryDirectives.paginated(AppConfig.PaginationConfig(0, 50, 100))) {
        pagination =>
          pagination shouldEqual Pagination(from, size)
          complete(StatusCodes.Accepted)
      }

      Get("/") ~> routes(paginated(0L, 50)) ~> check {
        status shouldEqual StatusCodes.Accepted
      }
      Get("/?size=42") ~> routes(paginated(0L, 42)) ~> check {
        status shouldEqual StatusCodes.Accepted
      }
      Get("/?from=1") ~> routes(paginated(1L, 50)) ~> check {
        status shouldEqual StatusCodes.Accepted
      }
      Get("/?from=1&size=42") ~> routes(paginated(1L, 42)) ~> check {
        status shouldEqual StatusCodes.Accepted
      }
      Get("/?from=1&size=-42") ~> routes(paginated(1L, 0)) ~> check {
        status shouldEqual StatusCodes.Accepted
      }
      Get("/?from=1&size=500") ~> routes(paginated(1L, 100)) ~> check {
        status shouldEqual StatusCodes.Accepted
      }
    }

    "handle deprecation" in {
      def deprecated(expected: Option[Boolean]) = (get & QueryDirectives.deprecated) { deprecation =>
        deprecation shouldEqual expected
        complete(StatusCodes.Accepted)
      }

      Get("/?deprecated=true") ~> routes(deprecated(Some(true))) ~> check {
        status shouldEqual StatusCodes.Accepted
      }
      Get("/?deprecated=false") ~> routes(deprecated(Some(false))) ~> check {
        status shouldEqual StatusCodes.Accepted
      }
      Get("/") ~> routes(deprecated(None)) ~> check {
        status shouldEqual StatusCodes.Accepted
      }
    }

    "extract resource path" in {
      def extract(expected: Path) = (get & QueryDirectives.extractResourcePath) { path =>
        path shouldEqual expected
        complete(StatusCodes.Accepted)
      }

      Get("/") ~> routes(extract(Path./)) ~> check {
        status shouldEqual StatusCodes.Accepted
      }
      Get("/foo/bar") ~> routes(extract(Path("/foo/bar").right.value)) ~> check {
        status shouldEqual StatusCodes.Accepted
      }
      Get("/foo/bar/") ~> routes(extract(Path("/foo/bar/").right.value)) ~> check {
        status shouldEqual StatusCodes.Accepted
      }
      Get("/a//b//c///") ~> routes(extract(Path("/a/b/c").right.value)) ~> check {
        status shouldEqual StatusCodes.BadRequest
        responseAs[Error] shouldEqual Error(classNameOf[IllegalParameter.type],
                                            Some("Path '/a//b//c///' cannot contain double slash."),
                                            errorCtxUri.asString)
      }
    }

    "extract organization" in {
      def extract(resource: Path, expected: String) = (get & QueryDirectives.extractOrg(resource)) { o =>
        o shouldEqual expected
        complete(StatusCodes.Accepted)
      }

      Get("/") ~> routes(extract(Path./, "")) ~> check {
        status shouldEqual StatusCodes.BadRequest
        responseAs[Error] shouldEqual Error(classNameOf[IllegalParameter.type],
                                            Some("Organization path cannot be empty."),
                                            errorCtxUri.asString)
      }
      Get("/foo/bar") ~> routes(extract(Path("/foo/bar").right.value, "bar")) ~> check {
        status shouldEqual StatusCodes.Accepted
      }
      Get("/foo/bar/") ~> routes(extract(Path("/foo/bar/").right.value, "bar")) ~> check {
        status shouldEqual StatusCodes.Accepted
      }
    }

    "extract project and organization" in {
      def extract(resource: Path, org: String, proj: String) = (get & QueryDirectives.extractProject(resource)) {
        case (o, p) =>
          o shouldEqual org
          p shouldEqual proj
          complete(StatusCodes.Accepted)
      }

      Get("/") ~> routes(extract(Path./, "", "")) ~> check {
        status shouldEqual StatusCodes.BadRequest
        responseAs[Error] shouldEqual Error(classNameOf[IllegalParameter.type],
                                            Some("Path '/' is not a valid project reference."),
                                            errorCtxUri.asString)
      }
      Get("/foo") ~> routes(extract(Path("/foo").right.value, "foo", "")) ~> check {
        status shouldEqual StatusCodes.BadRequest
        responseAs[Error] shouldEqual Error(classNameOf[IllegalParameter.type],
                                            Some("Path '/foo' is not a valid project reference."),
                                            errorCtxUri.asString)
      }
      Get("/foo/bar") ~> routes(extract(Path("/foo/bar").right.value, "foo", "bar")) ~> check {
        status shouldEqual StatusCodes.Accepted
      }
      Get("/foo/bar/") ~> routes(extract(Path("/foo/bar/").right.value, "foo", "bar")) ~> check {
        status shouldEqual StatusCodes.Accepted
      }
    }
  }
}

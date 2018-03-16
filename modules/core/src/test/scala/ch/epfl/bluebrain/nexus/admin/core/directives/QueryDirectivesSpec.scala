package ch.epfl.bluebrain.nexus.admin.core.directives

import java.net.URLEncoder

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.testkit.ScalatestRouteTest
import ch.epfl.bluebrain.nexus.admin.core.Error
import ch.epfl.bluebrain.nexus.admin.core.config.AppConfig.PaginationConfig
import ch.epfl.bluebrain.nexus.admin.core.directives.QueryDirectives._
import ch.epfl.bluebrain.nexus.admin.core.routes._
import ch.epfl.bluebrain.nexus.admin.ld.Const._
import ch.epfl.bluebrain.nexus.admin.ld.{Const, JsonLD}
import ch.epfl.bluebrain.nexus.admin.query._
import ch.epfl.bluebrain.nexus.commons.http.ContextUri
import ch.epfl.bluebrain.nexus.commons.http.JsonLdCirceSupport._
import ch.epfl.bluebrain.nexus.commons.test.Resources
import ch.epfl.bluebrain.nexus.commons.types.search.Sort.OrderType
import ch.epfl.bluebrain.nexus.commons.types.search.{Pagination, Sort, SortList}
import eu.timepit.refined.auto._
import io.circe.Json
import io.circe.generic.auto._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{CancelAfterFailure, Matchers, WordSpecLike}
import ch.epfl.bluebrain.nexus.admin.ld.JsonLD._

class QueryDirectivesSpec
    extends WordSpecLike
    with ScalatestRouteTest
    with Matchers
    with Resources
    with ScalaFutures
    with CancelAfterFailure {

  private val defaultContext = Const.filterContext.contextValue
  private val addedContext   = jsonContentOf("/query/added-context.json")

  private case class Response(pagination: Pagination, queryPayload: QueryPayload)

  private val errorContext = ContextUri("http://localhost/v0/contexts/nexus/core/error/v0.1.0")

  private implicit val pageConfig = PaginationConfig(0L, 50, 300)

  private val success = Json.obj("code" -> Json.fromString("success"))

  private def route(
      expectedResp: Response = Response(pageConfig.pagination, QueryPayload(`@context` = defaultContext))) =
    (handleExceptions(ExceptionHandling.exceptionHandler(errorContext)) & handleRejections(
      RejectionHandling.rejectionHandler(errorContext))) {
      (get & paramsToQuery) { (pagination, query) =>
        Response(pagination, query) shouldEqual expectedResp
        complete(success)
      }
    }

  "A searchQueryParams directive" should {

    "extract default page when not provided" in {
      Get("/") ~> route {
        Response(pageConfig.pagination, QueryPayload(`@context` = defaultContext))
      } ~> check {
        responseAs[Json] shouldEqual success
      }
    }

    "extract provided page" in {
      Get("/?from=1&size=30") ~> route {
        Response(Pagination(1L, 30), QueryPayload(`@context` = defaultContext))
      } ~> check {
        responseAs[Json] shouldEqual success
      }
    }

    "extract 0 when size and from are negative" in {
      Get("/?from=-1&size=-30") ~> route {
        Response(Pagination(0L, 0), QueryPayload(`@context` = defaultContext))
      } ~> check {
        responseAs[Json] shouldEqual success
      }
    }

    "extract maximum page size when provided is greater" in {
      Get("/?from=1&size=500") ~> route {
        Response(Pagination(1L, pageConfig.maxSize), QueryPayload(`@context` = defaultContext))
      } ~> check {
        responseAs[Json] shouldEqual success
      }
    }

    "extract deprecated and q query params when provided" in {
      Get("/?deprecated=false&q=something") ~> route {
        Response(pageConfig.pagination,
                 QueryPayload(`@context` = defaultContext, deprecated = Some(false), q = Some("something")))
      } ~> check {
        responseAs[Json] shouldEqual success
      }
    }

    "extract the JSON-LD output format when provided" in {
      Get("/?format=flattened") ~> route {
        Response(pageConfig.pagination, QueryPayload(`@context` = defaultContext, format = JsonLdFormat.Flattened))
      } ~> check {
        responseAs[Json] shouldEqual success
      }
      Get("/?format=expanded") ~> route {
        Response(pageConfig.pagination, QueryPayload(`@context` = defaultContext, format = JsonLdFormat.Expanded))
      } ~> check {
        responseAs[Json] shouldEqual success
      }
      Get("/?format=compacted") ~> route {
        Response(pageConfig.pagination, QueryPayload(`@context` = defaultContext, format = JsonLdFormat.Compacted))
      } ~> check {
        responseAs[Json] shouldEqual success
      }
    }

    "reject when an invalid JSON-LD output format is provided" in {
      Get("/?format=foobar") ~> route() ~> check {
        status shouldEqual StatusCodes.BadRequest
        responseAs[Error] shouldEqual Error("IllegalParam",
                                            Some("Could not find JsonLdFormat for 'foobar'"),
                                            "http://localhost/v0/contexts/nexus/core/error/v0.1.0")
      }
    }

    "reject when invalid fields provided" in {
      Get("/?fields=a,b,c") ~> route() ~> check {
        status shouldEqual StatusCodes.BadRequest
        responseAs[Error] shouldEqual Error("IllegalParam",
                                            Some("Could not expand field 'a': DownArray"),
                                            "http://localhost/v0/contexts/nexus/core/error/v0.1.0")
      }
    }

    "reject when invalid JSON" in {
      Get(s"/?context=${URLEncoder.encode("""{"a": b}]""", "UTF-8")}") ~> route() ~> check {
        status shouldEqual StatusCodes.BadRequest
        responseAs[Error] shouldEqual Error("WrongOrInvalidJson",
          None,
          "http://localhost/v0/contexts/nexus/core/error/v0.1.0")
      }
    }

    "extract fields, pagination, q and deprecated when provided" in {
      Get("/?deprecated=true&q=something&from=1&size=30&fields=nxv:one,schema:two,rdf:three,,") ~> route {
        Response(
          Pagination(1L, 30),
          QueryPayload(
            `@context` = defaultContext,
            deprecated = Some(true),
            q = Some("something"),
            fields = Set(Field(nxv.build("one").id), Field(schema.build("two").id), Field(rdf.build("three").id))
          )
        )

      } ~> check {
        responseAs[Json] shouldEqual success
      }
    }

    "extract sort when provided" in {
      val rdfType = rdf.tpe.id.value.replace("#", "%23")
      Get(s"/?sort=-nxv:createdAtTime,${rdfType},,,") ~> route {
        val expectedSort =
          SortList(List(Sort(OrderType.Desc, nxv.createdAtTime.id.value), Sort(OrderType.Asc, rdf.tpe.id.value)))
        Response(pageConfig.pagination, QueryPayload(`@context` = defaultContext, sort = expectedSort))
      } ~> check {
        responseAs[Json] shouldEqual success
      }
    }

    "merge context when specified as JSON object" in {
      Get(s"/?context=${URLEncoder.encode(JsonLD(addedContext).contextValue.noSpaces, "UTF-8")}") ~> route {
        Response(pageConfig.pagination, QueryPayload(`@context` = addedContext.contextValue deepMerge defaultContext))
      } ~> check {
        responseAs[Json] shouldEqual success
      }
    }
  }
}

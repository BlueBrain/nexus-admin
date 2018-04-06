package ch.epfl.bluebrain.nexus.admin.service.routes

import akka.http.scaladsl.model.headers.{Location, `Content-Type`}
import akka.http.scaladsl.model.{ContentTypes, StatusCodes}
import akka.http.scaladsl.testkit.ScalatestRouteTest
import ch.epfl.bluebrain.nexus.admin.core.config.AppConfig.DescriptionConfig
import ch.epfl.bluebrain.nexus.commons.http.JsonLdCirceSupport._
import io.circe.Json
import org.scalatest.{Matchers, OptionValues, WordSpecLike}

class StaticRoutesSpec extends WordSpecLike with Matchers with ScalatestRouteTest with OptionValues {

  private implicit val descConfig = DescriptionConfig("admin")
  private val routes              = StaticRoutes().routes

  "The Admin service" should {

    "return the appropriate service description" in {
      Get("/") ~> routes ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[Json] shouldEqual Json.obj("name"    -> Json.fromString(descConfig.name),
                                              "version" -> Json.fromString(descConfig.version))
      }
    }

    "redirect docs/admin to docs/admin/" in {
      Get("/docs/admin") ~> routes ~> check {
        status shouldEqual StatusCodes.MovedPermanently
        response.header[Location].value.uri.path.toString shouldEqual "/docs/admin/"
      }
    }

    "return documentation/" in {
      Get("/docs/admin/") ~> routes ~> check {
        status shouldEqual StatusCodes.OK
        response.header[`Content-Type`].value.contentType shouldEqual ContentTypes.`text/html(UTF-8)`
      }
    }
  }
}

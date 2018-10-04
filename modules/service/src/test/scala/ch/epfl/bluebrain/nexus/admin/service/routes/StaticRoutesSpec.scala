package ch.epfl.bluebrain.nexus.admin.service.routes

import akka.http.scaladsl.model.StatusCodes
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
  }
}

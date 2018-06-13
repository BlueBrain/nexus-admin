package ch.epfl.bluebrain.nexus.admin.service.encoders

import java.util.UUID

import cats.syntax.show._
import ch.epfl.bluebrain.nexus.admin.core.TestHelper
import ch.epfl.bluebrain.nexus.admin.core.config.AppConfig.ProjectsConfig
import ch.epfl.bluebrain.nexus.admin.core.resources.Resource
import ch.epfl.bluebrain.nexus.admin.core.types.Ref._
import ch.epfl.bluebrain.nexus.admin.ld.Const.nxv
import ch.epfl.bluebrain.nexus.admin.service.encoders.project._
import eu.timepit.refined.auto._
import io.circe.Json
import io.circe.syntax._
import org.scalatest.{Matchers, WordSpecLike}

import scala.concurrent.duration._

class ProjectEncoderSpec extends WordSpecLike with Matchers with TestHelper {

  private implicit val projConfig: ProjectsConfig =
    ProjectsConfig(3 seconds, "https://nexus.example.ch/v1/projects/", 100000L)
  private implicit val projNamespace = projConfig.namespace
  "ProjectEncoder" should {
    "encode project resource as JSON" in {

      val resource = Resource(genProjectReference(), UUID.randomUUID().toString, 1L, genProjectValue(), false)
      resource.asJson shouldEqual Json
        .obj(
          "@id"         -> Json.fromString(s"https://nexus.example.ch/v1/projects/${resource.id.value.show}"),
          "@type"       -> Json.fromString(nxv.Project.curie.show),
          "label"       -> Json.fromString(resource.id.value.projectLabel),
          "_rev"        -> Json.fromLong(resource.rev),
          "_deprecated" -> Json.fromBoolean(resource.deprecated),
          "_uuid"       -> Json.fromString(resource.uuid)
        )
        .deepMerge(resource.value)

    }
  }

}

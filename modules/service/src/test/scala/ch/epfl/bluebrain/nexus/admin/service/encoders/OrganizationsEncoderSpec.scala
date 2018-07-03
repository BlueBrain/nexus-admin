package ch.epfl.bluebrain.nexus.admin.service.encoders

import java.util.UUID

import cats.syntax.show._
import ch.epfl.bluebrain.nexus.admin.core.TestHelper
import ch.epfl.bluebrain.nexus.admin.core.config.AppConfig.OrganizationsConfig
import ch.epfl.bluebrain.nexus.admin.core.resources.Resource
import ch.epfl.bluebrain.nexus.admin.core.types.Ref._
import ch.epfl.bluebrain.nexus.admin.ld.Const.nxv
import ch.epfl.bluebrain.nexus.admin.service.encoders.organization._
import eu.timepit.refined.auto._
import io.circe.Json
import io.circe.syntax._
import org.scalatest.{Matchers, WordSpecLike}

import scala.concurrent.duration._

class OrganizationsEncoderSpec extends WordSpecLike with Matchers with TestHelper {

  private implicit val orgConfig: OrganizationsConfig =
    OrganizationsConfig(3 seconds, "https://nexus.example.ch/v1/orgs/")

  private implicit val orgNamespace = orgConfig.namespace
  "Organization" should {
    "encode organization resource as JSON" in {

      val ref      = genOrgReference()
      val resource = Resource(ref, ref.value, UUID.randomUUID().toString, 1L, genOrganizationValue, false)
      resource.asJson shouldEqual Json
        .obj(
          "@id"         -> Json.fromString(s"https://nexus.example.ch/v1/orgs/${resource.id.value.value}"),
          "@type"       -> Json.fromString(nxv.Organization.curie.show),
          "label"       -> Json.fromString(resource.id.value.value),
          "_rev"        -> Json.fromLong(resource.rev),
          "_deprecated" -> Json.fromBoolean(resource.deprecated),
          "_uuid"       -> Json.fromString(resource.uuid)
        )
        .deepMerge(resource.value)

    }
  }

}

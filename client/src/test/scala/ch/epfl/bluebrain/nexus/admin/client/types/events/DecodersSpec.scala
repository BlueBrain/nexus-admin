package ch.epfl.bluebrain.nexus.admin.client.types.events

import java.time.Instant
import java.util.UUID

import ch.epfl.bluebrain.nexus.admin.client.types.events.Event._
import ch.epfl.bluebrain.nexus.admin.client.types.events.decoders._
import ch.epfl.bluebrain.nexus.commons.test.Resources
import ch.epfl.bluebrain.nexus.iam.client.config.IamClientConfig
import ch.epfl.bluebrain.nexus.iam.client.types.Identity.User
import ch.epfl.bluebrain.nexus.rdf.syntax.node.unsafe._
import org.scalatest.{EitherValues, Matchers, WordSpecLike}

class DecodersSpec extends WordSpecLike with Matchers with Resources with EitherValues {

  implicit val iamConfig = IamClientConfig(url"http://iam.nexus.example.com/v1".value)

  val instant     = Instant.parse("2018-12-04T11:31:30.00Z")
  val subject     = User("example-user", "example-realm")
  val orgUuid     = UUID.fromString("4cd2c88b-ed73-4fd7-8afb-315032239a56")
  val projectUuid = UUID.fromString("a5c736f2-0ace-48f4-a7a7-56a15123d0b3")
  val mappings = Map("nxv" -> url"https://bluebrain.github.io/nexus/vocabulary/".value,
                     "rdf" -> url"http://www.w3.org/1999/02/22-rdf-syntax-ns#type".value)
  val base = url"https://nexus.example.com/base".value

  "Encoders and decoders" should {

    "decode project created event" in {
      val event: Event =
        ProjectCreated(projectUuid, orgUuid, "project label", Some("description"), mappings, base, instant, subject)
      val json = jsonContentOf("/kafka/project-created.json")

      json.as[Event].right.value shouldEqual event
    }

    "decode project update event" in {
      val event: Event =
        ProjectUpdated(projectUuid, "project label", Some("description"), mappings, base, 2L, instant, subject)
      val json = jsonContentOf("/kafka/project-updated.json")

      json.as[Event].right.value shouldEqual event
    }

    "decode project deprecated event" in {
      val event: Event =
        ProjectDeprecated(projectUuid, 3L, instant, subject)
      val json = jsonContentOf("/kafka/project-deprecated.json")

      json.as[Event].right.value shouldEqual event
    }

    "decode organization created event" in {
      val event: Event =
        OrganizationCreated(orgUuid, "organization label", "organization description", instant, subject)
      val json = jsonContentOf("/kafka/organization-created.json")

      json.as[Event].right.value shouldEqual event
    }
    "decode organization updated event" in {
      val event: Event =
        OrganizationUpdated(orgUuid, 2L, "organization label", "organization description", instant, subject)
      val json = jsonContentOf("/kafka/organization-updated.json")

      json.as[Event].right.value shouldEqual event
    }

    "decode organization deprecated event" in {
      val event: Event = OrganizationDeprecated(orgUuid, 3L, instant, subject)
      val json         = jsonContentOf("/kafka/organization-deprecated.json")

      json.as[Event].right.value shouldEqual event
    }

  }

}

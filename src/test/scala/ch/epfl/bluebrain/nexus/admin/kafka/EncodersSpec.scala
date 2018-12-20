package ch.epfl.bluebrain.nexus.admin.kafka

import java.time.Instant
import java.util.UUID

import ch.epfl.bluebrain.nexus.admin.kafka.encoders._
import ch.epfl.bluebrain.nexus.admin.organizations.OrganizationEvent._
import ch.epfl.bluebrain.nexus.admin.organizations.{Organization, OrganizationEvent}
import ch.epfl.bluebrain.nexus.admin.projects.ProjectEvent
import ch.epfl.bluebrain.nexus.admin.projects.ProjectEvent._
import ch.epfl.bluebrain.nexus.commons.test.Resources
import ch.epfl.bluebrain.nexus.iam.client.config.IamClientConfig
import ch.epfl.bluebrain.nexus.iam.client.types.Identity.User
import ch.epfl.bluebrain.nexus.rdf.syntax.node.unsafe._
import io.circe.syntax._
import org.scalatest.{Matchers, WordSpecLike}

class EncodersSpec extends WordSpecLike with Matchers with Resources {

  implicit val iamConfig = IamClientConfig("v1", url"http://iam.nexus.example.com/".value)

  val instant     = Instant.parse("2018-12-04T11:31:30.00Z")
  val subject     = User("example-user", "example-realm")
  val orgUuid     = UUID.fromString("4cd2c88b-ed73-4fd7-8afb-315032239a56")
  val projectUuid = UUID.fromString("a5c736f2-0ace-48f4-a7a7-56a15123d0b3")
  val mappings = Map("nxv" -> url"https://bluebrain.github.io/nexus/vocabulary/".value,
                     "rdf" -> url"http://www.w3.org/1999/02/22-rdf-syntax-ns#type".value)
  val base = url"https://nexus.example.com/base".value

  "Encoders" should {

    "encode project created event" in {
      val event: ProjectEvent =
        ProjectCreated(projectUuid, orgUuid, "project label", Some("description"), mappings, base, 1L, instant, subject)
      event.asJson shouldEqual jsonContentOf("/kafka/project-created.json")
    }

    "encode project update event" in {
      val event: ProjectEvent =
        ProjectUpdated(projectUuid, "project label", Some("description"), mappings, base, 2L, instant, subject)
      event.asJson shouldEqual jsonContentOf("/kafka/project-updated.json")
    }
    "encode project deprecated event" in {
      val event: ProjectEvent =
        ProjectDeprecated(projectUuid, 3L, instant, subject)

      event.asJson shouldEqual jsonContentOf("/kafka/project-deprecated.json")
    }

    "encode organization created event" in {
      val event: OrganizationEvent =
        OrganizationCreated(orgUuid,
                            1L,
                            Organization("organization label", "organization description"),
                            instant,
                            subject)
      event.asJson shouldEqual jsonContentOf("/kafka/organization-created.json")
    }
    "encode organization updated event" in {
      val event: OrganizationEvent =
        OrganizationUpdated(orgUuid,
                            2L,
                            Organization("organization label", "organization description"),
                            instant,
                            subject)
      event.asJson shouldEqual jsonContentOf("/kafka/organization-updated.json")
    }
    "encode organization deprecated event" in {
      val event: OrganizationEvent = OrganizationDeprecated(orgUuid, 3L, instant, subject)
      event.asJson shouldEqual jsonContentOf("/kafka/organization-deprecated.json")

    }

  }

}

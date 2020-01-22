package ch.epfl.bluebrain.nexus.admin.client.types.events

import java.time.Instant
import java.util.UUID

import ch.epfl.bluebrain.nexus.admin.client.types.events.Event._
import ch.epfl.bluebrain.nexus.commons.test.{EitherValues, Resources}
import ch.epfl.bluebrain.nexus.iam.client.config.IamClientConfig
import ch.epfl.bluebrain.nexus.iam.client.types.Identity.User
import ch.epfl.bluebrain.nexus.rdf.implicits._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

//noinspection TypeAnnotation
class DecodersSpec extends AnyWordSpecLike with Matchers with Resources with EitherValues {

  implicit val iamConfig = IamClientConfig(
    url"https://nexus.example.com",
    url"http://localhost:8080",
    "v1"
  )

  val instant            = Instant.EPOCH
  val subject            = User("uuid", "myrealm")
  val orgUuid            = UUID.fromString("d8cf3015-1bce-4dda-ba80-80cd4b5281e5")
  val orgLabel           = "thelabel"
  val orgDescription     = Some("the description")
  val projectLabel       = "theprojectlabel"
  val projectUuid        = UUID.fromString("94463ac0-3e9b-4261-80f5-e4253956eee2")
  val projectDescription = Some("the project description")
  val mappings = Map(
    "nxv" -> url"https://bluebrain.github.io/nexus/vocabulary/",
    "rdf" -> url"http://www.w3.org/1999/02/22-rdf-syntax-ns#"
  )
  val base  = url"http://localhost:8080/base/"
  val vocab = url"http://localhost:8080/vocab/"

  "Encoders and decoders" should {

    "decode project created event" in {
      val event: ProjectEvent =
        ProjectCreated(
          projectUuid,
          projectLabel,
          orgUuid,
          orgLabel,
          projectDescription,
          mappings,
          base,
          vocab,
          instant,
          subject
        )
      val json = jsonContentOf("/events/project-created.json")

      json.as[Event].rightValue shouldEqual event
    }

    "decode project update event" in {
      val event: ProjectEvent =
        ProjectUpdated(projectUuid, projectLabel, projectDescription, mappings, base, vocab, 2L, instant, subject)
      val json = jsonContentOf("/events/project-updated.json")

      json.as[Event].rightValue shouldEqual event
    }

    "decode project deprecated event" in {
      val event: ProjectEvent =
        ProjectDeprecated(projectUuid, 2L, instant, subject)
      val json = jsonContentOf("/events/project-deprecated.json")

      json.as[Event].rightValue shouldEqual event
    }

    "decode organization created event" in {
      val event: OrganizationEvent =
        OrganizationCreated(orgUuid, orgLabel, orgDescription, instant, subject)
      val json = jsonContentOf("/events/organization-created.json")

      json.as[Event].rightValue shouldEqual event
    }
    "decode organization updated event" in {
      val event: OrganizationEvent =
        OrganizationUpdated(orgUuid, 2L, orgLabel, None, instant, subject)
      val json = jsonContentOf("/events/organization-updated.json")

      json.as[Event].rightValue shouldEqual event
    }

    "decode organization deprecated event" in {
      val event: OrganizationEvent = OrganizationDeprecated(orgUuid, 2L, instant, subject)
      val json                     = jsonContentOf("/events/organization-deprecated.json")

      json.as[Event].rightValue shouldEqual event
    }

  }

}

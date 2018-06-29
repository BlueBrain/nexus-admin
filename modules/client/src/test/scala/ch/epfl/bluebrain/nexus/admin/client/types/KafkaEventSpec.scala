package ch.epfl.bluebrain.nexus.admin.client.types

import java.time.Instant

import ch.epfl.bluebrain.nexus.admin.client.types.KafkaEvent.{
  OrganizationCreated,
  OrganizationDeprecated,
  OrganizationUpdated,
  ProjectCreated,
  ProjectDeprecated,
  ProjectUpdated
}
import ch.epfl.bluebrain.nexus.commons.test.Resources
import ch.epfl.bluebrain.nexus.commons.types.Meta
import ch.epfl.bluebrain.nexus.commons.types.identity.Identity.UserRef
import io.circe._
import org.scalatest.{EitherValues, Inspectors, Matchers, WordSpecLike}

class KafkaEventSpec extends WordSpecLike with Matchers with Inspectors with Resources with EitherValues {

  "Kafka events" should {
    val projId      = "https://nexus.example.ch/v1/projects/some-id"
    val projUuid    = "ac9056c7-bedc-461f-8992-d17e994e39de"
    val meta        = Meta(UserRef("realm3", "alice"), Instant.parse("2018-06-25T17:01:06.249Z"))
    val createdJson = Json.obj("foo" -> Json.fromString("bar"))
    val updatedJson = Json.obj("foo" -> Json.fromString("bar2"))

    val orgId   = "https://nexus.example.ch/v1/orgs/some-id"
    val orgUuid = "cc9056c7-bedc-461f-8992-d17e994e39de"

    val list = List(
      jsonContentOf("/kafka/proj-created.json")    -> ProjectCreated(projId, projUuid, 42L, meta, createdJson),
      jsonContentOf("/kafka/proj-updated.json")    -> ProjectUpdated(projId, projUuid, 43L, meta, updatedJson),
      jsonContentOf("/kafka/proj-deprecated.json") -> ProjectDeprecated(projId, projUuid, 44L, meta),
      jsonContentOf("/kafka/org-created.json")     -> OrganizationCreated(orgId, orgUuid, 1L, meta, createdJson),
      jsonContentOf("/kafka/org-updated.json")     -> OrganizationUpdated(orgId, orgUuid, 2L, meta, updatedJson),
      jsonContentOf("/kafka/org-deprecated.json")  -> OrganizationDeprecated(orgId, orgUuid, 3L, meta)
    )

    "be decoded properly" in {
      forAll(list) {
        case (json, ev) => json.as[KafkaEvent].right.value shouldEqual ev
      }
    }
  }
}

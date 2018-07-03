package ch.epfl.bluebrain.nexus.admin.client.types

import java.time.Instant

import ch.epfl.bluebrain.nexus.admin.client.types.KafkaEvent._
import ch.epfl.bluebrain.nexus.commons.test.Resources
import ch.epfl.bluebrain.nexus.commons.types.Meta
import ch.epfl.bluebrain.nexus.commons.types.identity.Identity.UserRef
import ch.epfl.bluebrain.nexus.rdf.Iri
import org.scalatest.{EitherValues, Inspectors, Matchers, WordSpecLike}

class KafkaEventSpec extends WordSpecLike with Matchers with Inspectors with Resources with EitherValues {

  "Kafka events" should {
    val projId   = "https://nexus.example.ch/v1/projects/some-id"
    val projUuid = "ac9056c7-bedc-461f-8992-d17e994e39de"
    val meta     = Meta(UserRef("realm3", "alice"), Instant.parse("2018-06-25T17:01:06.249Z"))
    val base     = Iri.url("https://nexus.example.ch").right.value
    val mappings = Map(
      "nxv"   -> Iri.url("https://bbp-nexus.epfl.ch/vocabs/nexus/core/terms/v0.1.0/").right.value,
      "shacl" -> Iri.url("https://bluebrain.github.io/nexus/schemas/shacl").right.value
    )
    val orgCreated  = OrganizationValue("bbp")
    val orgUpdated  = OrganizationValue("bbp2")
    val projCreated = ProjectValue("example", base, mappings)
    val projUpdated = ProjectValue("example2", base, mappings)
    val orgId       = "https://nexus.example.ch/v1/orgs/some-id"
    val orgUuid     = "cc9056c7-bedc-461f-8992-d17e994e39de"

    val list = List(
      jsonContentOf("/kafka/proj-created.json") -> ProjectCreated(projId,
                                                                  "some-id",
                                                                  projUuid,
                                                                  orgUuid,
                                                                  42L,
                                                                  meta,
                                                                  projCreated),
      jsonContentOf("/kafka/proj-updated.json")    -> ProjectUpdated(projId, projUuid, orgUuid, 43L, meta, projUpdated),
      jsonContentOf("/kafka/proj-deprecated.json") -> ProjectDeprecated(projId, projUuid, orgUuid, 44L, meta),
      jsonContentOf("/kafka/org-created.json")     -> OrganizationCreated(orgId, orgUuid, 1L, meta, orgCreated),
      jsonContentOf("/kafka/org-updated.json")     -> OrganizationUpdated(orgId, orgUuid, 2L, meta, orgUpdated),
      jsonContentOf("/kafka/org-deprecated.json")  -> OrganizationDeprecated(orgId, orgUuid, 3L, meta)
    )

    "be decoded properly" in {
      forAll(list) {
        case (json, ev) => json.as[KafkaEvent].right.value shouldEqual ev
      }
    }
  }
}

package ch.epfl.bluebrain.nexus.admin.client.types

import java.time.Instant

import ch.epfl.bluebrain.nexus.admin.client.types.KafkaEvent.ProjectCreated
import ch.epfl.bluebrain.nexus.commons.types.Meta
import ch.epfl.bluebrain.nexus.commons.types.identity.Identity.UserRef
import io.circe._
import io.circe.parser._
import org.scalatest.{Matchers, WordSpecLike}

class KafkaEventSpec extends WordSpecLike with Matchers {

  "Kafka events" should {
    "be decoded properly" in {
      val json =
        """
          |{
          | "type": "project",
          | "state": "created",
          | "id": "https://nexus.example.ch/v1/projects/some-id",
          | "uuid": "ac9056c7-bedc-461f-8992-d17e994e39de",
          | "rev": 42,
          | "meta": {
          |   "author": {"id": "realms/realm3/users/alice", "type": "UserRef"},
          |   "instant": "2018-06-25T17:01:06.249Z"
          | },
          | "value": {"foo": "bar"}
          |}
        """.stripMargin

      decode[KafkaEvent](json) shouldEqual Right(
        ProjectCreated(
          "https://nexus.example.ch/v1/projects/some-id",
          "ac9056c7-bedc-461f-8992-d17e994e39de",
          42L,
          Meta(UserRef("realm3", "alice"), Instant.parse("2018-06-25T17:01:06.249Z")),
          Json.obj("foo" -> Json.fromString("bar"))
        ))
    }
  }
}

package ch.epfl.bluebrain.nexus.admin.service.encoders

import java.time.Instant

import ch.epfl.bluebrain.nexus.admin.core.resources.ResourceEvent
import ch.epfl.bluebrain.nexus.admin.core.resources.ResourceEvent.ResourceCreated
import ch.epfl.bluebrain.nexus.admin.service.encoders.kafka._
import ch.epfl.bluebrain.nexus.commons.types.Meta
import ch.epfl.bluebrain.nexus.commons.types.identity.Identity.UserRef
import io.circe._
import io.circe.syntax._
import eu.timepit.refined.auto._
import org.scalatest.{Matchers, WordSpecLike}

class KafkaEncoderSpec extends WordSpecLike with Matchers {

  "Kafka events" should {
    "be encoded properly" in {
      val json = Json.obj(
        "type"       -> Json.fromString("ProjectCreated"),
        "id"         -> Json.fromString("https://nexus.example.ch/v1/projects/some-id"),
        "uuid"       -> Json.fromString("ac9056c7-bedc-461f-8992-d17e994e39de"),
        "parentUuid" -> Json.fromString("e42afa69-df96-477c-8c0d-f4617a8eb827"),
        "rev"        -> Json.fromLong(42L),
        "meta" -> Json.obj(
          "author" -> Json.obj(
            "id"   -> Json.fromString("realms/realm3/users/alice"),
            "type" -> Json.fromString("UserRef")
          ),
          "instant" -> Json.fromString("2018-06-25T17:01:06.249Z")
        ),
        "value" -> Json.obj("foo" -> Json.fromString("bar"))
      )

      implicit val encoder: Encoder[ResourceEvent] = resourceEventEncoder("project")

      val event: ResourceEvent = ResourceCreated(
        "https://nexus.example.ch/v1/projects/some-id",
        "ac9056c7-bedc-461f-8992-d17e994e39de",
        Some("e42afa69-df96-477c-8c0d-f4617a8eb827"),
        42L,
        Meta(UserRef("realm3", "alice"), Instant.parse("2018-06-25T17:01:06.249Z")),
        Set("project"),
        Json.obj("foo" -> Json.fromString("bar"))
      )

      event.asJson shouldEqual json
    }
  }
}

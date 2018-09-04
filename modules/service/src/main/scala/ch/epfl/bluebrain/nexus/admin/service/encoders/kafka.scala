package ch.epfl.bluebrain.nexus.admin.service.encoders

import ch.epfl.bluebrain.nexus.admin.core.resources.ResourceEvent
import ch.epfl.bluebrain.nexus.admin.core.resources.ResourceEvent._
import ch.epfl.bluebrain.nexus.admin.refined.ld.Id
import ch.epfl.bluebrain.nexus.commons.http.syntax.circe._
import ch.epfl.bluebrain.nexus.commons.types.identity.Identity._
import ch.epfl.bluebrain.nexus.commons.types.Meta
import ch.epfl.bluebrain.nexus.service.kafka.key.Key
import io.circe._
import io.circe.java8.time._
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.semiauto.deriveEncoder

// TODO: Define and use JSON-LD vocabulary
object kafka {

  private implicit val config: Configuration = Configuration.default.withDiscriminator("type")

  private implicit val idEncoder: Encoder[Id] = Encoder.encodeString.contramap(_.value)

  private implicit val metaEncoder: Encoder[Meta] = deriveEncoder[Meta]

  private def prefix(event: ResourceEvent): String =
    if (event.parentUuid.isDefined) "Project" else "Organization"

  /**
    * Explicitly constructs a Circe encoder for resource events to be published to Kafka.
    *
    */
  implicit val resourceEventEncoder: Encoder[ResourceEvent] = {
    val baseEncoder = deriveEncoder[ResourceEvent]
    Encoder.encodeJson.contramap[ResourceEvent] {
      case rc: ResourceCreated =>
        baseEncoder(rc)
          .removeKeys("tags")
          .deepMerge(Json.obj("type" -> Json.fromString(s"${prefix(rc)}Created")))
      case ru: ResourceUpdated =>
        baseEncoder(ru)
          .removeKeys("tags")
          .deepMerge(Json.obj("type" -> Json.fromString(s"${prefix(ru)}Updated")))
      case rd: ResourceDeprecated =>
        baseEncoder(rd)
          .removeKeys("tags")
          .deepMerge(Json.obj("type" -> Json.fromString(s"${prefix(rd)}Deprecated")))
    }
  }

  implicit val resourceEventKey: Key[ResourceEvent] = Key.key { e =>
    e.parentUuid.getOrElse(e.uuid)
  }
}

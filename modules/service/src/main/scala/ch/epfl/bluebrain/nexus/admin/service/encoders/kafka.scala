package ch.epfl.bluebrain.nexus.admin.service.encoders

import ch.epfl.bluebrain.nexus.admin.core.resources.ResourceEvent
import ch.epfl.bluebrain.nexus.admin.core.resources.ResourceEvent._
import ch.epfl.bluebrain.nexus.admin.refined.ld.Id
import ch.epfl.bluebrain.nexus.commons.http.JsonOps._
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

  /**
    * Explicitly constructs a Circe encoder for resource events to be published to Kafka.
    *
    * @param resourceType the resource type literal tag, e.g. "project" or "organization"
    */
  def resourceEventEncoder(resourceType: String): Encoder[ResourceEvent] = {
    val prefix      = resourceType.capitalize
    val baseEncoder = deriveEncoder[ResourceEvent]
    Encoder.encodeJson.contramap[ResourceEvent] {
      case rc: ResourceCreated =>
        baseEncoder(rc)
          .removeKeys("tags")
          .deepMerge(Json.obj("type" -> Json.fromString(s"${prefix}Created")))
      case ru: ResourceUpdated =>
        baseEncoder(ru)
          .removeKeys("tags")
          .deepMerge(Json.obj("type" -> Json.fromString(s"${prefix}Updated")))
      case rd: ResourceDeprecated =>
        baseEncoder(rd)
          .removeKeys("tags")
          .deepMerge(Json.obj("type" -> Json.fromString(s"${prefix}Deprecated")))
    }
  }

  implicit val resourceEventKey: Key[ResourceEvent] = Key.key(_.uuid)
}

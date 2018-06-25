package ch.epfl.bluebrain.nexus.admin.service.encoders

import ch.epfl.bluebrain.nexus.commons.types.identity.Identity._
import ch.epfl.bluebrain.nexus.admin.core.resources.ResourceEvent
import ch.epfl.bluebrain.nexus.admin.core.resources.ResourceEvent._
import ch.epfl.bluebrain.nexus.commons.types.Meta
import ch.epfl.bluebrain.nexus.service.kafka.key.Key
import io.circe._
import io.circe.java8.time._
import io.circe.syntax._
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.semiauto.deriveEncoder

object kafka {

  private implicit val config: Configuration = Configuration.default.withDiscriminator("type")

  private implicit val metaEncoder: Encoder[Meta] = deriveEncoder[Meta]

  /**
    * Constructs explicitly a Circe encoder for resource events to be published to Kafka.

    * @param resourceType the resource type literal tag, e.g. "project" or "organization"
    */
  def resourceEventEncoder(resourceType: String): Encoder[ResourceEvent] = Encoder.encodeJson.contramap {
    case ResourceCreated(id, uuid, rev, meta, _, value) =>
      Json.obj(
        "type"  -> Json.fromString(resourceType),
        "state" -> Json.fromString("created"),
        "id"    -> Json.fromString(id.value),
        "uuid"  -> Json.fromString(uuid),
        "rev"   -> Json.fromLong(rev),
        "meta"  -> meta.asJson,
        "value" -> value
      )
    case ResourceUpdated(id, uuid, rev, meta, _, value) =>
      Json.obj(
        "type"  -> Json.fromString(resourceType),
        "state" -> Json.fromString("updated"),
        "id"    -> Json.fromString(id.value),
        "uuid"  -> Json.fromString(uuid),
        "rev"   -> Json.fromLong(rev),
        "meta"  -> meta.asJson,
        "value" -> value
      )
    case ResourceDeprecated(id, uuid, rev, meta, _) =>
      Json.obj(
        "type"  -> Json.fromString(resourceType),
        "state" -> Json.fromString("deprecated"),
        "id"    -> Json.fromString(id.value),
        "uuid"  -> Json.fromString(uuid),
        "rev"   -> Json.fromLong(rev),
        "meta"  -> meta.asJson
      )
  }

  implicit val resourceEventKey: Key[ResourceEvent] = Key.key(_.uuid)
}

package ch.epfl.bluebrain.nexus.admin.kafka

import ch.epfl.bluebrain.nexus.admin.config.Contexts._
import ch.epfl.bluebrain.nexus.admin.organizations.{Organization, OrganizationEvent}
import ch.epfl.bluebrain.nexus.admin.projects.ProjectEvent
import ch.epfl.bluebrain.nexus.commons.types.identity.Identity
import ch.epfl.bluebrain.nexus.rdf.syntax.circe.context._
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.semiauto._
import io.circe.{Encoder, Json}
import io.circe.java8.time._

object encoders {

  private implicit val config: Configuration = Configuration.default.withDiscriminator("@type")

  private implicit val identityEncoder: Encoder[Identity] = Encoder.encodeJson.contramap { identity =>
    Json.fromString(identity.id.id)
  }

  private implicit val organizationEncoder: Encoder[Organization] = deriveEncoder[Organization]

  /**
    * Kafka encoder for [[ProjectEvent]]s.
    */
  implicit val projectEventEncoder: Encoder[ProjectEvent] = deriveEncoder[ProjectEvent]
    .mapJson(_.renameKey("id", "uuid").addContext(adminCtxUri))

  /**
    * Kafka Encoder for [[OrganizationEvent]]s.
    */
  implicit val organizationEventEncoder: Encoder[OrganizationEvent] = deriveEncoder[OrganizationEvent]
    .mapJson(_.renameKey("id", "uuid").addContext(adminCtxUri))

  private implicit class JsonOps(json: Json) {

    def renameKey(oldKey: String, newKey: String): Json = json.mapObject { obj =>
      obj(oldKey) match {
        case Some(value) => obj.remove(oldKey).add(newKey, value)
        case None        => obj
      }
    }

  }

}

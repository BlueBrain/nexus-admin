package ch.epfl.bluebrain.nexus.admin.kafka

import ch.epfl.bluebrain.nexus.admin.config.Contexts._
import ch.epfl.bluebrain.nexus.admin.organizations.{Organization, OrganizationEvent}
import ch.epfl.bluebrain.nexus.admin.projects.ProjectEvent
import ch.epfl.bluebrain.nexus.iam.client.config.IamClientConfig
import ch.epfl.bluebrain.nexus.iam.client.types.Identity.Subject
import ch.epfl.bluebrain.nexus.rdf.instances._
import ch.epfl.bluebrain.nexus.rdf.syntax.circe.context._
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.semiauto._
import io.circe.syntax._
import io.circe.{Encoder, Json}

object encoders {

  private implicit val config: Configuration = Configuration.default.withDiscriminator("@type")

  private implicit def identityEncoder(implicit iamClientConfig: IamClientConfig): Encoder[Subject] =
    Encoder.encodeJson.contramap { subject =>
      subject.id.asJson
    }

  private implicit val organizationEncoder: Encoder[Organization] = deriveEncoder[Organization]

  /**
    * Kafka encoder for [[ProjectEvent]]s.
    */
  implicit def projectEventEncoder(implicit iamClientConfig: IamClientConfig): Encoder[ProjectEvent] = {
    val enc = deriveEncoder[ProjectEvent].mapJson(_.renameKey("id", "uuid").addContext(adminCtxUri))
    Encoder.instance(event => enc(event) deepMerge Json.obj("rev" -> Json.fromLong(event.rev)))
  }

  /**
    * Kafka Encoder for [[OrganizationEvent]]s.
    */
  implicit def organizationEventEncoder(implicit iamClientConfig: IamClientConfig): Encoder[OrganizationEvent] = {
    val enc = deriveEncoder[OrganizationEvent].mapJson(_.renameKey("id", "uuid").addContext(adminCtxUri))
    Encoder.instance(event => enc(event) deepMerge Json.obj("rev" -> Json.fromLong(event.rev)))
  }

  private implicit class JsonOps(json: Json) {

    def renameKey(oldKey: String, newKey: String): Json = json.mapObject { obj =>
      obj(oldKey) match {
        case Some(value) => obj.remove(oldKey).add(newKey, value)
        case None        => obj
      }
    }

  }

}

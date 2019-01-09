package ch.epfl.bluebrain.nexus.admin.kafka

import ch.epfl.bluebrain.nexus.admin.config.Contexts._
import ch.epfl.bluebrain.nexus.admin.organizations.OrganizationEvent
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

  private implicit val config: Configuration = Configuration.default
    .withDiscriminator("@type")
    .copy(transformMemberNames = {
      case "id"           => "_uuid"
      case "label"        => "_label"
      case "rev"          => "_rev"
      case "instant"      => "_instant"
      case "subject"      => "_subject"
      case "organization" => "_organization"
      case other          => other
    })

  private implicit def identityEncoder(implicit iamClientConfig: IamClientConfig): Encoder[Subject] =
    Encoder.encodeJson.contramap(_.id.asJson)

  /**
    * Kafka encoder for [[ProjectEvent]]s.
    */
  implicit def projectEventEncoder(implicit iamClientConfig: IamClientConfig): Encoder[ProjectEvent] = {
    val enc = deriveEncoder[ProjectEvent]
    Encoder.instance(event => enc(event) deepMerge Json.obj("_rev" -> Json.fromLong(event.rev)).addContext(adminCtxUri))
  }

  /**
    * Kafka Encoder for [[OrganizationEvent]]s.
    */
  implicit def organizationEventEncoder(implicit iamClientConfig: IamClientConfig): Encoder[OrganizationEvent] = {
    val enc = deriveEncoder[OrganizationEvent]
    Encoder.instance(event => enc(event) deepMerge Json.obj("_rev" -> Json.fromLong(event.rev)).addContext(adminCtxUri))
  }

}

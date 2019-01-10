package ch.epfl.bluebrain.nexus.admin.kafka

import ch.epfl.bluebrain.nexus.admin.config.Contexts._
import ch.epfl.bluebrain.nexus.admin.organizations.OrganizationEvent
import ch.epfl.bluebrain.nexus.admin.projects.ProjectEvent
import ch.epfl.bluebrain.nexus.admin.projects.ProjectEvent.{ProjectCreated, ProjectUpdated}
import ch.epfl.bluebrain.nexus.iam.client.config.IamClientConfig
import ch.epfl.bluebrain.nexus.iam.client.types.Identity.Subject
import ch.epfl.bluebrain.nexus.rdf.Iri.AbsoluteIri
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
      case "id"                => "_uuid"
      case "label"             => "_label"
      case "rev"               => "_rev"
      case "instant"           => "_instant"
      case "subject"           => "_subject"
      case "organizationLabel" => "_organizationLabel"
      case "organizationUuid"  => "_organizationUuid"
      case other               => other
    })

  private implicit def identityEncoder(implicit iamClientConfig: IamClientConfig): Encoder[Subject] =
    Encoder.encodeJson.contramap(_.id.asJson)

  /**
    * Kafka encoder for [[ProjectEvent]]s.
    */
  implicit def projectEventEncoder(implicit iamClientConfig: IamClientConfig): Encoder[ProjectEvent] = {
    val enc = deriveEncoder[ProjectEvent]
    Encoder.instance { event =>
      val common = enc(event) deepMerge Json.obj("_rev" -> Json.fromLong(event.rev)).addContext(adminCtxUri)
      event match {
        case c: ProjectCreated => addApiMappings(common, c.apiMappings)
        case u: ProjectUpdated => addApiMappings(common, u.apiMappings)
        case _                 => common
      }

    }
  }

  /**
    * Kafka Encoder for [[OrganizationEvent]]s.
    */
  implicit def organizationEventEncoder(implicit iamClientConfig: IamClientConfig): Encoder[OrganizationEvent] = {
    val enc = deriveEncoder[OrganizationEvent]
    Encoder.instance(event => enc(event) deepMerge Json.obj("_rev" -> Json.fromLong(event.rev)).addContext(adminCtxUri))
  }

  private def addApiMappings(json: Json, apiMappings: Map[String, AbsoluteIri]): Json = json.mapObject { obj =>
    obj.add(
      "apiMappings",
      Json.arr(apiMappings.toList.map {
        case (prefix, namespace) =>
          Json.obj("prefix" -> Json.fromString(prefix), "namespace" -> Json.fromString(namespace.asString))
      }: _*)
    )
  }

}

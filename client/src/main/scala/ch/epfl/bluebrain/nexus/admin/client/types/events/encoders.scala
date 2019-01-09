package ch.epfl.bluebrain.nexus.admin.client.types.events

import ch.epfl.bluebrain.nexus.iam.client.config.IamClientConfig
import ch.epfl.bluebrain.nexus.iam.client.types.Identity.Subject
import ch.epfl.bluebrain.nexus.rdf.Iri.AbsoluteIri
import ch.epfl.bluebrain.nexus.rdf.instances._
import ch.epfl.bluebrain.nexus.rdf.syntax.circe.context._
import ch.epfl.bluebrain.nexus.rdf.syntax.node.unsafe._
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.semiauto.deriveEncoder
import io.circe.syntax._
import io.circe.{Encoder, Json}

object encoders {

  val adminCtxUri: AbsoluteIri = url"https://bluebrain.github.io/nexus/contexts/admin.json".value

  private implicit val config: Configuration = Configuration.default
    .withDiscriminator("@type")
    .copy(transformMemberNames = {
      case "id"  => "uuid"
      case other => other
    })

  private implicit def subjectEncoder(implicit iamClientConfig: IamClientConfig): Encoder[Subject] =
    Encoder.encodeJson.contramap(_.id.asJson)

  /**
    * [[Encoder]] for [[ProjectEvent]]s.
    */
  implicit def projectEventEncoder(implicit iamClientConfig: IamClientConfig): Encoder[ProjectEvent] = {
    val enc = deriveEncoder[ProjectEvent]
    Encoder.instance(event => enc(event) deepMerge Json.obj("rev" -> Json.fromLong(event.rev)).addContext(adminCtxUri))
  }

  /**
    * [[Encoder]] for [[OrganizationEvent]]s.
    */
  implicit def organizationEventEncoder(implicit iamClientConfig: IamClientConfig): Encoder[OrganizationEvent] = {
    val enc = deriveEncoder[OrganizationEvent]
    Encoder.instance(event => enc(event) deepMerge Json.obj("rev" -> Json.fromLong(event.rev)).addContext(adminCtxUri))
  }

}

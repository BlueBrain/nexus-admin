package ch.epfl.bluebrain.nexus.admin.client.types.events

import ch.epfl.bluebrain.nexus.iam.client.config.IamClientConfig
import ch.epfl.bluebrain.nexus.iam.client.types.Identity
import ch.epfl.bluebrain.nexus.iam.client.types.Identity.Subject
import ch.epfl.bluebrain.nexus.rdf.Iri.AbsoluteIri
import ch.epfl.bluebrain.nexus.rdf.instances._
import ch.epfl.bluebrain.nexus.rdf.syntax.node.unsafe._
import io.circe.Decoder
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.semiauto._

object decoders {

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

  private implicit def subjectDecoder(implicit iamClientConfig: IamClientConfig): Decoder[Subject] =
    Decoder.decodeString.flatMap { id =>
      Identity(url"$id".value) match {
        case Some(s: Subject) => Decoder.const(s)
        case _                => Decoder.failedWithMessage(s"Couldn't decode subject from '$id'")
      }

    }

  private final case class Mapping(prefix: String, namespace: AbsoluteIri)

  private implicit val mappingDecoder: Decoder[Mapping] = deriveDecoder[Mapping]

  private implicit val mapDecoder: Decoder[Map[String, AbsoluteIri]] =
    Decoder.decodeList[Mapping].map(_.map(m => (m.prefix, m.namespace)).toMap)

  /**
    * [[Decoder]] for [[Event]]s.
    */
  implicit def organizationEventDecoder(implicit iamClientConfig: IamClientConfig): Decoder[Event] =
    deriveDecoder[Event]

}

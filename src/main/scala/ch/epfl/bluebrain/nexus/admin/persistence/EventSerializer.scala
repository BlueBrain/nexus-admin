package ch.epfl.bluebrain.nexus.admin.persistence

import java.nio.charset.StandardCharsets.UTF_8

import akka.actor.ExtendedActorSystem
import akka.serialization.SerializerWithStringManifest
import ch.epfl.bluebrain.nexus.admin.config.Settings
import ch.epfl.bluebrain.nexus.admin.organizations.OrganizationEvent
import ch.epfl.bluebrain.nexus.admin.projects.ProjectEvent
import ch.epfl.bluebrain.nexus.iam.client.config.IamClientConfig
import ch.epfl.bluebrain.nexus.rdf.Iri
import ch.epfl.bluebrain.nexus.rdf.Iri.AbsoluteIri
import io.circe.{Decoder, Encoder, Printer}
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.parser.parse

class EventSerializer(system: ExtendedActorSystem) extends SerializerWithStringManifest {

  private implicit val iamClientConfig: IamClientConfig = Settings(system).appConfig.iam

  private implicit val config: Configuration = Configuration.default.withDiscriminator("@type")
  private implicit val printer: Printer      = Printer.noSpaces.copy(dropNullValues = true)

  private implicit val iriDecoder: Decoder[AbsoluteIri] = Decoder.decodeString.emap(Iri.absolute)
  private implicit val iriEncoder: Encoder[AbsoluteIri] = Encoder.encodeString.contramap(_.asString)

  private implicit val projectEventDecoder: Decoder[ProjectEvent]           = deriveDecoder[ProjectEvent]
  private implicit val projectEventEncoder: Encoder[ProjectEvent]           = deriveEncoder[ProjectEvent]
  private implicit val organizationEventDecoder: Decoder[OrganizationEvent] = deriveDecoder[OrganizationEvent]
  private implicit val organizationEventEncoder: Encoder[OrganizationEvent] = deriveEncoder[OrganizationEvent]

  override val identifier: Int = 1129 // "nexus-admin".getBytes.map(_.toInt).sum

  override def manifest(o: AnyRef): String = o match {
    case _: OrganizationEvent => "organization-event"
    case _: ProjectEvent      => "project-event"
    case other =>
      throw new IllegalArgumentException(
        s"Cannot determine manifest for unknown type: '${other.getClass.getCanonicalName}'")
  }

  override def toBinary(o: AnyRef): Array[Byte] = o match {
    case oe: OrganizationEvent => organizationEventEncoder(oe).pretty(printer).getBytes(UTF_8)
    case pe: ProjectEvent      => projectEventEncoder(pe).pretty(printer).getBytes(UTF_8)
    case other =>
      throw new IllegalArgumentException(s"Cannot serialize unknown type: '${other.getClass.getCanonicalName}'")
  }

  override def fromBinary(bytes: Array[Byte], manifest: String): AnyRef = {
    val str = new String(bytes, UTF_8)
    manifest match {
      case "organization-event" =>
        parse(str)
          .flatMap(organizationEventDecoder.decodeJson)
          .getOrElse(throw new IllegalArgumentException(s"Cannot deserialize value: '$str' to 'OrganizationEvent'"))
      case "project-event" =>
        parse(str)
          .flatMap(projectEventDecoder.decodeJson)
          .getOrElse(throw new IllegalArgumentException(s"Cannot deserialize value: '$str' to 'ProjectEvent'"))
      case other =>
        throw new IllegalArgumentException(s"Cannot deserialize value: '$str' with unknown manifest: '$other'")
    }
  }
}

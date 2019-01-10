package ch.epfl.bluebrain.nexus.admin.client.types.events

import java.time.Instant
import java.util.UUID

import ch.epfl.bluebrain.nexus.admin.client.types.events.ProjectEvent._
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
    .copy(transformConstructorNames = {
      case "ProjectCreatedDto"    => "ProjectCreated"
      case "ProjectUpdatedDto"    => "ProjectUpdated"
      case "ProjectDeprecatedDto" => "ProjectDeprecated"
      case other                  => other
    })

  private implicit def subjectDecoder(implicit iamClientConfig: IamClientConfig): Decoder[Subject] =
    Decoder.decodeString.flatMap { id =>
      Identity(url"$id".value) match {
        case Some(s: Subject) => Decoder.const(s)
        case _                => Decoder.failedWithMessage(s"Couldn't decode subject from '$id'")
      }

    }

  private implicit val mappingDecoder: Decoder[Mapping] = deriveDecoder[Mapping]

  /**
    * [[Decoder]] for [[ProjectEvent]]s.
    */
  implicit def projectEventDecoder(implicit iamClientConfig: IamClientConfig): Decoder[ProjectEvent] =
    deriveDecoder[ProjectEventDto].map(_.toEvent)

  /**
    * [[Decoder]] for [[OrganizationEvent]]s.
    */
  implicit def organizationEventDecoder(implicit iamClientConfig: IamClientConfig): Decoder[OrganizationEvent] =
    deriveDecoder[OrganizationEvent]

  private sealed trait ProjectEventDto extends Product with Serializable {

    def id: UUID

    def rev: Long

    def instant: Instant

    def subject: Subject

    def toEvent: ProjectEvent
  }

  private final case class Mapping(prefix: String, namespace: AbsoluteIri)

  private final case class ProjectCreatedDto(id: UUID,
                                             organization: UUID,
                                             label: String,
                                             description: Option[String],
                                             apiMappings: List[Mapping],
                                             base: AbsoluteIri,
                                             instant: Instant,
                                             subject: Subject)
      extends ProjectEventDto {

    val rev: Long = 1L
    override def toEvent: ProjectEvent =
      ProjectCreated(id,
                     organization,
                     label,
                     description,
                     apiMappings.map(m => (m.prefix, m.namespace)).toMap,
                     base,
                     instant,
                     subject)
  }

  private final case class ProjectUpdatedDto(id: UUID,
                                             label: String,
                                             description: Option[String],
                                             apiMappings: List[Mapping],
                                             base: AbsoluteIri,
                                             rev: Long,
                                             instant: Instant,
                                             subject: Subject)
      extends ProjectEventDto {
    override def toEvent: ProjectEvent =
      ProjectUpdated(id,
                     label,
                     description,
                     apiMappings.map(m => (m.prefix, m.namespace)).toMap,
                     base,
                     rev,
                     instant,
                     subject)
  }

  private final case class ProjectDeprecatedDto(id: UUID, rev: Long, instant: Instant, subject: Subject)
      extends ProjectEventDto {
    override def toEvent: ProjectEvent = ProjectDeprecated(id, rev, instant, subject)
  }

}

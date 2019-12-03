package ch.epfl.bluebrain.nexus.admin.client.types.events

import java.time.Instant
import java.util.UUID

import ch.epfl.bluebrain.nexus.iam.client.types.Identity
import ch.epfl.bluebrain.nexus.iam.client.types.Identity.Subject
import ch.epfl.bluebrain.nexus.rdf.Iri
import ch.epfl.bluebrain.nexus.rdf.instances._
import ch.epfl.bluebrain.nexus.rdf.Iri.AbsoluteIri
import com.github.ghik.silencer.silent
import io.circe.Decoder
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.semiauto.deriveConfiguredDecoder

/**
  * Enumeration of organization and project events.
  */
sealed trait Event extends Product with Serializable {

  /**
    * @return the permanent identifier for the resource
    */
  def id: UUID

  /**
    * @return the revision that this event generated
    */
  def rev: Long

  /**
    * @return the instant when this event was created
    */
  def instant: Instant

  /**
    * @return the subject which created this event
    */
  def subject: Subject
}

sealed trait OrganizationEvent extends Event

sealed trait ProjectEvent extends Event

object Event {

  /**
    * Event representing organization creation.
    *
    * @param id           the permanent identifier of the organization
    * @param label        the organization label
    * @param description  the optional organization description
    * @param instant      the instant when this event was created
    * @param subject      the subject which created this event
    */
  final case class OrganizationCreated(
      id: UUID,
      label: String,
      description: Option[String],
      instant: Instant,
      subject: Subject
  ) extends OrganizationEvent {

    /**
      *  the revision number that this event generates
      */
    val rev: Long = 1L
  }

  /**
    * Event representing organization update.
    *
    * @param id           the permanent identifier of the organization
    * @param rev          the update revision
    * @param label        the organization label
    * @param description  the optional organization description
    * @param instant      the instant when this event was created
    * @param subject      the subject which created this event
    */
  final case class OrganizationUpdated(
      id: UUID,
      rev: Long,
      label: String,
      description: Option[String],
      instant: Instant,
      subject: Subject
  ) extends OrganizationEvent

  /**
    * Event representing organization deprecation.
    *
    * @param id           the permanent identifier of the organization
    * @param rev          the deprecation revision
    * @param instant      the instant when this event was created
    * @param subject      the subject which created this event
    */
  final case class OrganizationDeprecated(
      id: UUID,
      rev: Long,
      instant: Instant,
      subject: Subject
  ) extends OrganizationEvent

  /**
    * Event representing project creation.
    *
    * @param id                the permanent identifier for the project
    * @param label             the label (segment) of the project
    * @param organizationUuid  the permanent identifier for the parent organization
    * @param organizationLabel the parent organization label
    * @param description       an optional project description
    * @param apiMappings       the API mappings
    * @param base              the base IRI for generated resource IDs
    * @param vocab             an optional vocabulary for resources with no context
    * @param instant           the timestamp associated to this event
    * @param subject           the identity associated to this event
    */
  final case class ProjectCreated(
      id: UUID,
      label: String,
      organizationUuid: UUID,
      organizationLabel: String,
      description: Option[String],
      apiMappings: Map[String, AbsoluteIri],
      base: AbsoluteIri,
      vocab: AbsoluteIri,
      instant: Instant,
      subject: Subject
  ) extends ProjectEvent {

    /**
      *  the revision number that this event generates
      */
    val rev: Long = 1L
  }

  /**
    * Event representing project update.
    *
    * @param id          the permanent identifier for the project
    * @param label       the label (segment) of the project
    * @param description an optional project description
    * @param apiMappings the API mappings
    * @param base        the base IRI for generated resource IDs
    * @param vocab       an optional vocabulary for resources with no context
    * @param rev         the revision number that this event generates
    * @param instant     the timestamp associated to this event
    * @param subject     the identity associated to this event
    */
  final case class ProjectUpdated(
      id: UUID,
      label: String,
      description: Option[String],
      apiMappings: Map[String, AbsoluteIri],
      base: AbsoluteIri,
      vocab: AbsoluteIri,
      rev: Long,
      instant: Instant,
      subject: Subject
  ) extends ProjectEvent

  /**
    * Event representing project deprecation.
    *
    * @param id         the permanent identifier for the project
    * @param rev        the revision number that this event generates
    * @param instant    the timestamp associated to this event
    * @param subject    the identity associated to this event
    */
  final case class ProjectDeprecated(
      id: UUID,
      rev: Long,
      instant: Instant,
      subject: Subject
  ) extends ProjectEvent

  @silent
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

  @silent
  private implicit val subjectDecoder: Decoder[Subject] =
    Decoder.decodeString.flatMap { id =>
      Iri.absolute(id) match {
        case Left(_) => Decoder.failedWithMessage(s"Couldn't decode iri from '$id'")
        case Right(iri) =>
          Identity(iri) match {
            case Some(s: Subject) => Decoder.const(s)
            case _                => Decoder.failedWithMessage(s"Couldn't decode subject from '$id'")
          }
      }
    }

  private final case class Mapping(prefix: String, namespace: AbsoluteIri)

  @silent
  private implicit val mappingDecoder: Decoder[Mapping] = deriveConfiguredDecoder[Mapping]

  @silent
  private implicit val mapDecoder: Decoder[Map[String, AbsoluteIri]] =
    Decoder.decodeList[Mapping].map(_.map(m => (m.prefix, m.namespace)).toMap)

  /**
    * [[Decoder]] for [[Event]]s.
    */
  implicit val eventDecoder: Decoder[Event] =
    deriveConfiguredDecoder[Event]
}

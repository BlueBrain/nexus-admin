package ch.epfl.bluebrain.nexus.admin.client.types.events

import java.time.Instant
import java.util.UUID

import ch.epfl.bluebrain.nexus.iam.client.types.Identity.Subject
import ch.epfl.bluebrain.nexus.rdf.Iri.AbsoluteIri

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
    * @param description  the organization description
    * @param instant      the instant when this event was created
    * @param subject      the subject which created this event
    */
  final case class OrganizationCreated(id: UUID, label: String, description: String, instant: Instant, subject: Subject)
      extends OrganizationEvent {

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
    * @param description  the organization description
    * @param instant      the instant when this event was created
    * @param subject      the subject which created this event
    */
  final case class OrganizationUpdated(id: UUID,
                                       rev: Long,
                                       label: String,
                                       description: String,
                                       instant: Instant,
                                       subject: Subject)
      extends OrganizationEvent

  /**
    * Event representing organization deprecation.
    *
    * @param id           the permanent identifier of the organization
    * @param rev          the deprecation revision
    * @param instant      the instant when this event was created
    * @param subject      the subject which created this event
    */
  final case class OrganizationDeprecated(id: UUID, rev: Long, instant: Instant, subject: Subject)
      extends OrganizationEvent

  /**
    * Event representing project creation.
    *
    * @param id           the permanent identifier for the project
    * @param label        the label (segment) of the project
    * @param organization the permanent identifier for the parent organization
    * @param description  an optional project description
    * @param apiMappings  the API mappings
    * @param base         the base IRI for generated resource IDs
    * @param instant      the timestamp associated to this event
    * @param subject      the identity associated to this event
    */
  final case class ProjectCreated(id: UUID,
                                  organization: UUID,
                                  label: String,
                                  description: Option[String],
                                  apiMappings: Map[String, AbsoluteIri],
                                  base: AbsoluteIri,
                                  instant: Instant,
                                  subject: Subject)
      extends ProjectEvent {

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
    * @param rev         the revision number that this event generates
    * @param instant     the timestamp associated to this event
    * @param subject     the identity associated to this event
    */
  final case class ProjectUpdated(id: UUID,
                                  label: String,
                                  description: Option[String],
                                  apiMappings: Map[String, AbsoluteIri],
                                  base: AbsoluteIri,
                                  rev: Long,
                                  instant: Instant,
                                  subject: Subject)
      extends ProjectEvent

  /**
    * Event representing project deprecation.
    *
    * @param id         the permanent identifier for the project
    * @param rev        the revision number that this event generates
    * @param instant    the timestamp associated to this event
    * @param subject    the identity associated to this event
    */
  final case class ProjectDeprecated(id: UUID, rev: Long, instant: Instant, subject: Subject) extends ProjectEvent

}

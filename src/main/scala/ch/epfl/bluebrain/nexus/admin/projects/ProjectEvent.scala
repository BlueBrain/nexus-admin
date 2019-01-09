package ch.epfl.bluebrain.nexus.admin.projects

import java.time.Instant
import java.util.UUID

import ch.epfl.bluebrain.nexus.iam.client.types.Identity.Subject
import ch.epfl.bluebrain.nexus.rdf.Iri.AbsoluteIri

sealed trait ProjectEvent extends Product with Serializable {

  /**
    * @return the permanent identifier for the project
    */
  def id: UUID

  /**
    * @return the revision number that this event generates
    */
  def rev: Long

  /**
    * @return the timestamp associated to this event
    */
  def instant: Instant

  /**
    * @return the identity associated to this event
    */
  def subject: Subject
}

object ProjectEvent {

  /**
    * Evidence that a project has been created.
    *
    * @param id           the permanent identifier for the project
    * @param label        the label (segment) of the project
    * @param organization the permanent identifier for the parent organization
    * @param description  an optional project description
    * @param apiMappings  the API mappings
    * @param base         the base IRI for generated resource IDs
    * @param vocabulary   an optional vocabulary for resources with no context
    * @param instant      the timestamp associated to this event
    * @param subject      the identity associated to this event
    */
  final case class ProjectCreated(id: UUID,
                                  organization: UUID,
                                  label: String,
                                  description: Option[String],
                                  apiMappings: Map[String, AbsoluteIri],
                                  base: AbsoluteIri,
                                  vocabulary: Option[AbsoluteIri],
                                  instant: Instant,
                                  subject: Subject)
      extends ProjectEvent {

    /**
      *  the revision number that this event generates
      */
    val rev: Long = 1L
  }

  /**
    * Evidence that a project has been updated.
    *
    * @param id          the permanent identifier for the project
    * @param label       the label (segment) of the project
    * @param description an optional project description
    * @param apiMappings the API mappings
    * @param base        the base IRI for generated resource IDs
    * @param vocabulary  an optional vocabulary for resources with no context
    * @param rev         the revision number that this event generates
    * @param instant     the timestamp associated to this event
    * @param subject     the identity associated to this event
    */
  final case class ProjectUpdated(id: UUID,
                                  label: String,
                                  description: Option[String],
                                  apiMappings: Map[String, AbsoluteIri],
                                  base: AbsoluteIri,
                                  vocabulary: Option[AbsoluteIri],
                                  rev: Long,
                                  instant: Instant,
                                  subject: Subject)
      extends ProjectEvent

  /**
    * Evidence that a project has been deprecated.
    *
    * @param id         the permanent identifier for the project
    * @param rev        the revision number that this event generates
    * @param instant    the timestamp associated to this event
    * @param subject    the identity associated to this event
    */
  final case class ProjectDeprecated(id: UUID, rev: Long, instant: Instant, subject: Subject) extends ProjectEvent

}

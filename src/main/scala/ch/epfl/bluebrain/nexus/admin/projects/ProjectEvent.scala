package ch.epfl.bluebrain.nexus.admin.projects

import java.time.Instant
import java.util.UUID

import ch.epfl.bluebrain.nexus.commons.types.identity.Identity

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
  def subject: Identity
}

object ProjectEvent {

  /**
    * Evidence that a project has been created.
    *
    * @param id           the permanent identifier for the project
    * @param label        the label (segment) of the project
    * @param organization the permanent identifier for the parent organization
    * @param description  an optional project description
    * @param rev          the revision number that this event generates
    * @param instant      the timestamp associated to this event
    * @param subject      the identity associated to this event
    */
  final case class ProjectCreated(id: UUID,
                                  organization: UUID,
                                  label: ProjectLabel,
                                  description: Option[String],
                                  rev: Long,
                                  instant: Instant,
                                  subject: Identity)
      extends ProjectEvent

  /**
    * Evidence that a project has been updated.
    *
    * @param id          the permanent identifier for the project
    * @param label       the label (segment) of the project
    * @param description an optional project description
    * @param rev         the revision number that this event generates
    * @param instant     the timestamp associated to this event
    * @param subject     the identity associated to this event
    */
  final case class ProjectUpdated(id: UUID,
                                  label: ProjectLabel,
                                  description: Option[String],
                                  rev: Long,
                                  instant: Instant,
                                  subject: Identity)
      extends ProjectEvent

  /**
    * Evidence that a project has been deprecated.
    *
    * @param id         the permanent identifier for the project
    * @param rev        the revision number that this event generates
    * @param instant    the timestamp associated to this event
    * @param subject    the identity associated to this event
    */
  final case class ProjectDeprecated(id: UUID, rev: Long, instant: Instant, subject: Identity) extends ProjectEvent

}

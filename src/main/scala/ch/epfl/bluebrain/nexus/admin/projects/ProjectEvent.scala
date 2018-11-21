package ch.epfl.bluebrain.nexus.admin.projects

import java.util.UUID

import ch.epfl.bluebrain.nexus.commons.types.Meta

sealed trait ProjectEvent extends Product with Serializable {

  def id: UUID

  def meta: Meta
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
    * @param meta         the metadata associated to this event
    */
  final case class ProjectCreated(id: UUID,
                                  organization: UUID,
                                  label: ProjectLabel,
                                  description: Option[String],
                                  rev: Long,
                                  meta: Meta)
      extends ProjectEvent

  /**
    * Evidence that a project has been updated.
    *
    * @param id          the permanent identifier for the project
    * @param label       the label (segment) of the project
    * @param description an optional project description
    * @param rev         the revision number that this event generates
    * @param meta        the metadata associated to this event
    */
  final case class ProjectUpdated(id: UUID, label: ProjectLabel, description: Option[String], rev: Long, meta: Meta)
      extends ProjectEvent

  /**
    * Evidence that a project has been deprecated.
    *
    * @param id         the permanent identifier for the project
    * @param rev        the revision number that this event generates
    * @param meta       the metadata associated to this event
    */
  final case class ProjectDeprecated(id: UUID, rev: Long, meta: Meta) extends ProjectEvent

}

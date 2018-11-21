package ch.epfl.bluebrain.nexus.admin.projects

import java.util.UUID

import ch.epfl.bluebrain.nexus.commons.types.Meta

sealed trait ProjectCommand extends Product with Serializable {

  def id: UUID

  def meta: Meta
}

object ProjectCommand {

  /**
    * Command that signals the intent to create a new project.
    *
    * @param id           the permanent identifier for the project
    * @param organization the permanent identifier for the parent organization
    * @param label        the label (segment) of the project
    * @param description  an optional project description
    * @param meta         the metadata associated to this command
    */
  final case class CreateProject(id: UUID,
                                 organization: UUID,
                                 label: ProjectLabel,
                                 description: Option[String],
                                 meta: Meta)
      extends ProjectCommand

  /**
    * Command that signals the intent to update a project.
    *
    * @param id          the permanent identifier for the project
    * @param label       the label (segment) of the resource
    * @param description an optional project description
    * @param rev         the last known revision of the project
    * @param meta        the metadata associated to this command
    */
  final case class UpdateProject(id: UUID, label: ProjectLabel, description: Option[String], rev: Long, meta: Meta)
      extends ProjectCommand

  /**
    * Command that signals the intent to deprecate a project.
    *
    * @param id    the permanent identifier for the project
    * @param rev   the last known revision of the project
    * @param meta  the metadata associated to this command
    */
  final case class DeprecateProject(id: UUID, rev: Long, meta: Meta) extends ProjectCommand
}

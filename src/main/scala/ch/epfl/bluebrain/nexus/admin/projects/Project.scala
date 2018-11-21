package ch.epfl.bluebrain.nexus.admin.projects

import java.util.UUID

/**
  * Type that represents a project.
  *
  * @param id           the project permanent internal ID
  * @param organization the parent organization ID
  * @param label        the project label (segment)
  * @param description  an optional description
  * @param rev          the resource revision
  * @param deprecated   the deprecation status
  */
final case class Project(id: UUID,
                         organization: UUID,
                         label: ProjectLabel,
                         description: Option[String],
                         rev: Long,
                         deprecated: Boolean)

/**
  * Type that holds the actual project payload.
  *
  * @param label       the project label
  * @param description an optional project description
  */
final case class SimpleProject(label: String, description: Option[String])

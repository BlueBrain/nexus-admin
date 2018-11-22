package ch.epfl.bluebrain.nexus.admin.projects

import java.util.UUID

/**
  * Type that represents a project.
  *
  * @param id           the project internal ID
  * @param organization the parent organization ID
  * @param label        the project label (segment)
  * @param description  an optional description
  * @param rev          the resource revision
  * @param deprecated   the deprecation status
  */
case class Project(id: UUID,
                   organization: UUID,
                   label: ProjectLabel,
                   description: Option[String],
                   rev: Long,
                   deprecated: Boolean)

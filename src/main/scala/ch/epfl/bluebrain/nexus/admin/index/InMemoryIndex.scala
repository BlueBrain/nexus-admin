package ch.epfl.bluebrain.nexus.admin.index

import java.util.UUID

import ch.epfl.bluebrain.nexus.admin.organizations.Organization
import ch.epfl.bluebrain.nexus.admin.projects.{Project, ProjectLabel}

import scala.collection.mutable

/**
  * In-memory implementation of [[ch.epfl.bluebrain.nexus.admin.index.Index]].
  */
class InMemoryIndex extends Index {

  private val organizations      = mutable.HashMap.empty[UUID, Organization]
  private val organizationLabels = mutable.HashMap.empty[String, UUID]

  private val projects      = mutable.HashMap.empty[UUID, Project]
  private val projectLabels = mutable.HashMap.empty[ProjectLabel, UUID]

  override def update(organization: Organization): Boolean = {
    val update = organizations.contains(organization.id)
    organizations.update(organization.id, organization)
    organizationLabels.update(organization.label, organization.id)
    update
  }

  override def update(project: Project): Boolean = {
    val update = projects.contains(project.id)
    projects.update(project.id, project)
    projectLabels.update(project.label, project.id)
    update
  }

  override def getOrganization(id: UUID): Option[Organization] =
    organizations.get(id)

  override def getOrganization(label: String): Option[Organization] =
    organizationLabels.get(label).flatMap(getOrganization)

  override def getProject(id: UUID): Option[Project] =
    projects.get(id)

  override def getProject(label: ProjectLabel): Option[Project] =
    projectLabels.get(label).flatMap(getProject)
}

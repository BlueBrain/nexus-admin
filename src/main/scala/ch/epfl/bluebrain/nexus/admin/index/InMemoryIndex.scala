package ch.epfl.bluebrain.nexus.admin.index

import java.util.UUID

import ch.epfl.bluebrain.nexus.admin.organizations.Organization
import ch.epfl.bluebrain.nexus.admin.projects.{Project, ProjectLabel}
import ch.epfl.bluebrain.nexus.admin.types.ResourceF

import scala.collection.mutable

/**
  * In-memory implementation of [[ch.epfl.bluebrain.nexus.admin.index.Index]].
  */
class InMemoryIndex extends Index {

  private val organizations      = mutable.HashMap.empty[UUID, ResourceF[Organization]]
  private val organizationLabels = mutable.HashMap.empty[String, UUID]

  private val projects      = mutable.HashMap.empty[UUID, ResourceF[Project]]
  private val projectLabels = mutable.HashMap.empty[ProjectLabel, UUID]

  override def updateOrganization(organization: ResourceF[Organization]): Boolean = {
    val update = organizations.contains(organization.uuid)
    organizations.update(organization.uuid, organization)
    organizationLabels.update(organization.value.label, organization.uuid)
    update
  }

  override def updateProject(project: ResourceF[Project]): Boolean = {
    val update = projects.contains(project.uuid)
    projects.update(project.uuid, project)
    projectLabels.update(project.value.label, project.uuid)
    update
  }

  override def getOrganization(id: UUID): Option[ResourceF[Organization]] =
    organizations.get(id)

  override def getOrganization(label: String): Option[ResourceF[Organization]] =
    organizationLabels.get(label).flatMap(getOrganization)

  override def getProject(id: UUID): Option[ResourceF[Project]] =
    projects.get(id)

  override def getProject(label: ProjectLabel): Option[ResourceF[Project]] =
    projectLabels.get(label).flatMap(getProject)
}

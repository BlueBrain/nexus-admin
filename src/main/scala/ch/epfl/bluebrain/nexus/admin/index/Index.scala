package ch.epfl.bluebrain.nexus.admin.index

import java.util.UUID

import ch.epfl.bluebrain.nexus.admin.organizations.Organization
import ch.epfl.bluebrain.nexus.admin.projects.{Project, ProjectLabel}
import ch.epfl.bluebrain.nexus.admin.types.ResourceF

/**
  * Contract for an index of organizations and projects.
  */
trait Index {

  /**
    * Adds or replaces an organization in the index.
    *
    * @param organization the organization resource
    * @return true if a previous version of the organization was replaced,
    *         false otherwise
    */
  def updateOrganization(organization: ResourceF[Organization]): Boolean

  /**
    * Adds or replaces an project in the index.
    *
    * @param project the project resource
    * @return true if a previous version of the project was replaced,
    *         false otherwise
    */
  def updateProject(project: ResourceF[Project]): Boolean

  /**
    * @param id the organization permanent ID
    * @return the corresponding organization resource, if any
    */
  def getOrganization(id: UUID): Option[ResourceF[Organization]]

  /**
    * @param label the organization label
    * @return the corresponding organization resource, if any
    */
  def getOrganization(label: String): Option[ResourceF[Organization]]

  /**
    * @param id the project permanent ID
    * @return the corresponding project resource, if any
    */
  def getProject(id: UUID): Option[ResourceF[Project]]

  /**
    * @param label the project label
    * @return the corresponding project resource, if any
    */
  def getProject(label: ProjectLabel): Option[ResourceF[Project]]

}

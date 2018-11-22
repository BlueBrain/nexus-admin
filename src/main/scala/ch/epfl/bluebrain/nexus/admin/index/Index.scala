package ch.epfl.bluebrain.nexus.admin.index

import java.util.UUID

import ch.epfl.bluebrain.nexus.admin.organizations.Organization
import ch.epfl.bluebrain.nexus.admin.projects.{Project, ProjectLabel}

/**
  * Contract for an index of organizations and projects.
  */
trait Index {

  /**
    * Adds or replaces an organization in the index.
    *
    * @param organization the organization
    * @return true if a previous version of the organization was replaced,
    *         false otherwise
    */
  def update(organization: Organization): Boolean

  /**
    * Adds or replaces an project in the index.
    *
    * @param project the project
    * @return true if a previous version of the project was replaced,
    *         false otherwise
    */
  def update(project: Project): Boolean

  /**
    * @param id the organization permanent ID
    * @return the corresponding organization, if any
    */
  def getOrganization(id: UUID): Option[Organization]

  /**
    * @param label the organization label
    * @return the corresponding organization, if any
    */
  def getOrganization(label: String): Option[Organization]

  /**
    * @param id the project permanent ID
    * @return the corresponding project, if any
    */
  def getProject(id: UUID): Option[Project]

  /**
    * @param label the project label
    * @return the corresponding project instance, if any
    */
  def getProject(label: ProjectLabel): Option[Project]

}

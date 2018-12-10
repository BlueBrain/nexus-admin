package ch.epfl.bluebrain.nexus.admin.index

import ch.epfl.bluebrain.nexus.admin.organizations.Organization
import ch.epfl.bluebrain.nexus.admin.projects.Project
import ch.epfl.bluebrain.nexus.admin.types.ResourceF
import ch.epfl.bluebrain.nexus.commons.types.search.Pagination

/**
  * Contract for an index of organizations and projects.
  */
trait Index[F[_]] {

  /**
    * Adds or replaces an organization in the index.
    *
    * @param organization the organization resource
    * @return true if a previous version of the organization was replaced,
    *         false otherwise
    */
  def updateOrganization(organization: ResourceF[Organization]): F[Boolean]

  /**
    * Adds or replaces an project in the index.
    *
    * @param project the project resource
    * @return true if a previous version of the project was replaced,
    *         false otherwise
    */
  def updateProject(project: ResourceF[Project]): F[Boolean]

  /**
    * Fetch organization.
    *
    * @param label the organization label
    * @return the corresponding organization resource, if any
    */
  def getOrganization(label: String): F[Option[ResourceF[Organization]]]

  /**
    * Fetch project.
    *
    * @param project      the project label
    * @param organization the organization label
    * @return the corresponding project resource, if any
    */
  def getProject(organization: String, project: String): F[Option[ResourceF[Project]]]

  /**
    * List organizations.
    *
    * @param pagination pagination
    * @return list of organizations limited by pagination
    */
  def listOrganizations(pagination: Pagination): F[List[ResourceF[Organization]]]

  /**
    * List projects.
    *
    * @param pagination pagination
    * @return list of projects limited by pagination
    */
  def listProjects(pagination: Pagination): F[List[ResourceF[Project]]]

  /**
    * List projects for organization.
    *
    * @param organizationLabel  the label of organization
    * @param pagination         pagination
    * @return list of projects limited by pagination
    */
  def listProjects(organizationLabel: String, pagination: Pagination): F[List[ResourceF[Project]]]

}

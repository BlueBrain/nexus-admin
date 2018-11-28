package ch.epfl.bluebrain.nexus.admin.organizations

/**
  * Representation of an organization.
  *
  * @param label        the label of the organization, used e.g. in the HTTP URLs and @id
  * @param description  the description of the organization
  */
final case class Organization(label: String, description: String)

package ch.epfl.bluebrain.nexus.admin.projects

/**
  * Type that represents a project.
  *
  * @param label        the project label (segment)
  * @param organization the organization label
  * @param description  an optional description
  */
final case class Project(label: String, organization: String, description: Option[String]) {

  /**
    * @return full label for the project (including organization).
    */
  def fullLabel: String = s"$organization/$label"
}

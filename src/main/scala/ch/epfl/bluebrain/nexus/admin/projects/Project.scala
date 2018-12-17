package ch.epfl.bluebrain.nexus.admin.projects

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto._

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

object Project {

  implicit val projectEncoder: Encoder[Project] = deriveEncoder[Project]

  implicit val projectDecoder: Decoder[Project] = deriveDecoder[Project]
}

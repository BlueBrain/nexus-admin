package ch.epfl.bluebrain.nexus.admin.projects

import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder

/**
  * Type that represents a project payload for creation and update requests.
  *
  * @param description an optional description
  */
final case class ProjectDescription(description: Option[String])

object ProjectDescription {
  implicit val descriptionDecoder: Decoder[ProjectDescription] = deriveDecoder[ProjectDescription]
}

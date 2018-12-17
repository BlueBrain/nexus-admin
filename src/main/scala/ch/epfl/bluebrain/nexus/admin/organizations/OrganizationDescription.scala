package ch.epfl.bluebrain.nexus.admin.organizations

import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder

/**
  * Type that represents an organization payload for creation and update requests.
  *
  * @param description a description
  */
final case class OrganizationDescription(description: String)

object OrganizationDescription {
  implicit val descriptionDecoder: Decoder[OrganizationDescription] = deriveDecoder[OrganizationDescription]
}

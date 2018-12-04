package ch.epfl.bluebrain.nexus.admin.organizations

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto._

/**
  * Representation of an organization.
  *
  * @param label        the label of the organization, used e.g. in the HTTP URLs and @id
  * @param description  the description of the organization
  */
final case class Organization(label: String, description: String)

object Organization {

  implicit val organizationEncoder: Encoder[Organization] = deriveEncoder[Organization]

  implicit val organizationDecoder: Decoder[Organization] = deriveDecoder[Organization]
}

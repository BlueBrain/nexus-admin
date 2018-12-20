package ch.epfl.bluebrain.nexus.admin.organizations

import io.circe.{Encoder, Json}

/**
  * Representation of an organization.
  *
  * @param label        the label of the organization, used e.g. in the HTTP URLs and @id
  * @param description  the description of the organization
  */
final case class Organization(label: String, description: String)

object Organization {

  implicit val organizationEncoder: Encoder[Organization] = Encoder.encodeJson.contramap { o =>
    Json.obj("_label" -> Json.fromString(o.label), "description" -> Json.fromString(o.description))
  }
}

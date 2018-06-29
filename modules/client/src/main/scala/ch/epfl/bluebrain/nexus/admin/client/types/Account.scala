package ch.epfl.bluebrain.nexus.admin.client.types

import ch.epfl.bluebrain.nexus.admin.ld.Const.nxv
import io.circe.Decoder

/**
  * Data type exposing account information to external services.
  *
  * @param name       the name of the organization
  * @param rev        the selected revision for the organization
  * @param label      the label of the organization
  * @param deprecated the deprecation status of the organization
  * @param uuid       the permanent identifier for the organization
  */
final case class Account(name: String, rev: Long, label: String, deprecated: Boolean, uuid: String)

object Account {
  implicit def accountDecoder: Decoder[Account] =
    Decoder.instance { c =>
      for {
        name       <- c.downField(nxv.name.reference.value).as[String]
        rev        <- c.downField(nxv.rev.reference.value).as[Long]
        deprecated <- c.downField(nxv.deprecated.reference.value).as[Boolean]
        uuid       <- c.downField(nxv.uuid.reference.value).as[String]
        label      <- c.downField(nxv.label.reference.value).as[String]
      } yield Account(name, rev, label, deprecated, uuid)
    }
}

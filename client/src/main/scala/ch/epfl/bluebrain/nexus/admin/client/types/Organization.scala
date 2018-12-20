package ch.epfl.bluebrain.nexus.admin.client.types

import java.time.Instant
import java.util.UUID

import ch.epfl.bluebrain.nexus.rdf.Iri.AbsoluteIri
import ch.epfl.bluebrain.nexus.rdf.instances._
import io.circe.Decoder

/**
  * Organization with metadata.
  *
  * @param id           organization ID
  * @param label        organization label
  * @param description  organization description
  * @param uuid         organization permanent identifier
  * @param rev          organization revision
  * @param deprecated   organization deprecation status
  * @param createdAt    [[Instant]] at which the organization was created
  * @param createdBy    ID of the subject that created the organization
  * @param updatedAt    [[Instant]] at which the organization was updated
  * @param updatedBy    ID of the subject that updated the organization
  */
final case class Organization(id: AbsoluteIri,
                              label: String,
                              description: String,
                              uuid: UUID,
                              rev: Long,
                              deprecated: Boolean,
                              createdAt: Instant,
                              createdBy: AbsoluteIri,
                              updatedAt: Instant,
                              updatedBy: AbsoluteIri)

object Organization {

  /**
    * JSON decoder for [[Organization]].
    */
  implicit val organizationDecoder: Decoder[Organization] =
    Decoder.instance { hc =>
      for {
        id          <- hc.get[AbsoluteIri]("@id")
        label       <- hc.get[String]("label")
        description <- hc.get[String]("description")
        uuid        <- hc.get[String]("_uuid").map(UUID.fromString)
        rev         <- hc.get[Long]("_rev")
        deprecated  <- hc.get[Boolean]("_deprecated")
        createdBy   <- hc.get[AbsoluteIri]("_createdBy")
        createdAt   <- hc.get[Instant]("_createdAt")
        updatedBy   <- hc.get[AbsoluteIri]("_updatedBy")
        updatedAt   <- hc.get[Instant]("_updatedAt")
      } yield Organization(id, label, description, uuid, rev, deprecated, createdAt, createdBy, updatedAt, updatedBy)
    }
}

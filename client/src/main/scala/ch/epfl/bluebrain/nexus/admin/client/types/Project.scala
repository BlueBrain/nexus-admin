package ch.epfl.bluebrain.nexus.admin.client.types

import java.time.Instant
import java.util.UUID

import ch.epfl.bluebrain.nexus.rdf.Iri.AbsoluteIri
import ch.epfl.bluebrain.nexus.rdf.instances._
import io.circe.Decoder

/**
  * Project with metadata.
  *
  * @param id           project ID
  * @param label        project label
  * @param organization organization to which this project belongs
  * @param description  project description
  * @param uuid         project permanent identifier
  * @param rev          project revision
  * @param deprecated   project deprecation status
  * @param createdAt    [[Instant]] at which the project was created
  * @param createdBy    ID of the subject that created the project
  * @param updatedAt    [[Instant]] at which the project was updated
  * @param updatedBy    ID of the subject that updated the project
  */
final case class Project(id: AbsoluteIri,
                         label: String,
                         organization: String,
                         description: Option[String],
                         uuid: UUID,
                         rev: Long,
                         deprecated: Boolean,
                         createdAt: Instant,
                         createdBy: AbsoluteIri,
                         updatedAt: Instant,
                         updatedBy: AbsoluteIri)

object Project {

  /**
    * JSON decoder for [[Project]].
    */
  implicit val projectDecoder: Decoder[Project] =
    Decoder.instance { hc =>
      for {
        id           <- hc.get[AbsoluteIri]("@id")
        label        <- hc.get[String]("label")
        organization <- hc.get[String]("organization")
        description  <- hc.getOrElse[Option[String]]("description")(None)
        uuid         <- hc.get[String]("_uuid").map(UUID.fromString)
        rev          <- hc.get[Long]("_rev")
        deprecated   <- hc.get[Boolean]("_deprecated")
        createdBy    <- hc.get[AbsoluteIri]("_createdBy")
        createdAt    <- hc.get[Instant]("_createdAt")
        updatedBy    <- hc.get[AbsoluteIri]("_updatedBy")
        updatedAt    <- hc.get[Instant]("_updatedAt")
      } yield
        Project(id, label, organization, description, uuid, rev, deprecated, createdAt, createdBy, updatedAt, updatedBy)
    }
}

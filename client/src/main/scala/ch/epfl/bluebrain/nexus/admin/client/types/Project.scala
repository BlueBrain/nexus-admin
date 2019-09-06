package ch.epfl.bluebrain.nexus.admin.client.types

import java.time.Instant
import java.util.UUID

import cats.Show
import ch.epfl.bluebrain.nexus.rdf.Iri
import ch.epfl.bluebrain.nexus.rdf.Iri.AbsoluteIri
import ch.epfl.bluebrain.nexus.rdf.instances._
import io.circe.{Decoder, DecodingFailure}

/**
  * Project with metadata.
  *
  * @param id                project ID
  * @param label             project label
  * @param organizationLabel parent organization label
  * @param description       project description
  * @param base              the base IRI for generated resource IDs
  * @param vocab             an optional vocabulary for resources with no context
  * @param apiMappings       the API mappings
  * @param uuid              project permanent identifier
  * @param organizationUuid  parent organization uuid
  * @param rev               project revision
  * @param deprecated        project deprecation status
  * @param createdAt         [[Instant]] at which the project was created
  * @param createdBy         ID of the subject that created the project
  * @param updatedAt         [[Instant]] at which the project was updated
  * @param updatedBy         ID of the subject that updated the project
  */
final case class Project(
    id: AbsoluteIri,
    label: String,
    organizationLabel: String,
    description: Option[String],
    base: AbsoluteIri,
    vocab: AbsoluteIri,
    apiMappings: Map[String, AbsoluteIri],
    uuid: UUID,
    organizationUuid: UUID,
    rev: Long,
    deprecated: Boolean,
    createdAt: Instant,
    createdBy: AbsoluteIri,
    updatedAt: Instant,
    updatedBy: AbsoluteIri
)

object Project {

  private final case class Mapping(prefix: String, namespace: AbsoluteIri)

  private implicit val mappingDecoder: Decoder[Mapping] = Decoder.instance { hc =>
    for {
      prefix    <- hc.downField("prefix").as[String]
      namespace <- hc.downField("namespace").as[String]
      iri       <- Iri.absolute(namespace).left.map(err => DecodingFailure(err, hc.history))
    } yield Mapping(prefix, iri)
  }

  implicit val projectShow: Show[Project] = Show.show(project => s"${project.organizationLabel}/${project.label}")

  /**
    * JSON decoder for [[Project]].
    */
  implicit val projectDecoder: Decoder[Project] =
    Decoder.instance { hc =>
      for {
        id          <- hc.get[AbsoluteIri]("@id")
        orgLabel    <- hc.get[String]("_organizationLabel")
        orgUuid     <- hc.get[UUID]("_organizationUuid")
        description <- hc.get[Option[String]]("description")
        base        <- hc.get[AbsoluteIri]("base")
        vocab       <- hc.get[AbsoluteIri]("vocab")
        lam         <- hc.get[List[Mapping]]("apiMappings")
        apiMap = lam.map(am => am.prefix -> am.namespace).toMap
        label      <- hc.get[String]("_label")
        uuid       <- hc.get[UUID]("_uuid")
        rev        <- hc.get[Long]("_rev")
        deprecated <- hc.get[Boolean]("_deprecated")
        createdBy  <- hc.get[AbsoluteIri]("_createdBy")
        createdAt  <- hc.get[Instant]("_createdAt")
        updatedBy  <- hc.get[AbsoluteIri]("_updatedBy")
        updatedAt  <- hc.get[Instant]("_updatedAt")
      } yield Project(
        id,
        label,
        orgLabel,
        description,
        base,
        vocab,
        apiMap,
        uuid,
        orgUuid,
        rev,
        deprecated,
        createdAt,
        createdBy,
        updatedAt,
        updatedBy
      )
    }
}

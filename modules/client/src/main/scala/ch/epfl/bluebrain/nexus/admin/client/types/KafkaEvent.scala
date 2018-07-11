package ch.epfl.bluebrain.nexus.admin.client.types

import ch.epfl.bluebrain.nexus.admin.client.types.Project.{mappingToMapEntry, LoosePrefixMapping}
import ch.epfl.bluebrain.nexus.admin.ld.Const.nxv
import ch.epfl.bluebrain.nexus.commons.types.Meta
import ch.epfl.bluebrain.nexus.commons.types.identity.Identity._
import ch.epfl.bluebrain.nexus.rdf.Iri
import ch.epfl.bluebrain.nexus.rdf.Iri.AbsoluteIri
import io.circe.{Decoder, DecodingFailure}
import io.circe.java8.time._
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.semiauto._

/**
  * Data types and decoders to process Kafka events published by the admin service.
  */
// TODO: Define and use JSON-LD vocabulary
sealed trait KafkaEvent {
  def id: String
  def uuid: String
  def rev: Long
  def meta: Meta
}

object KafkaEvent {

  /**
    * @param name the project name
    * @param base the project base
    * @param prefixMappings the project prefix mappings
    */
  final case class ProjectValue(name: String, base: AbsoluteIri, prefixMappings: Map[String, AbsoluteIri])

  /**
    * A project that has been created.
    *
    * @param id         the unique identifier of the project
    * @param label      the label (segment) of the project
    * @param uuid       the permanent identifier for the project
    * @param parentUuid the permanent identifier for the organization
    * @param rev        the revision number that this event generates
    * @param meta       the metadata associated to this event
    * @param value      the payload of the project
    */
  final case class ProjectCreated(id: String,
                                  label: String,
                                  uuid: String,
                                  parentUuid: String,
                                  rev: Long,
                                  meta: Meta,
                                  value: ProjectValue)
      extends KafkaEvent

  /**
    * A project that has been updated.
    *
    * @param id         the unique identifier of the project
    * @param uuid       the permanent identifier for the project
    * @param parentUuid the permanent identifier for the organization
    * @param rev        the revision number that this event generates
    * @param meta       the metadata associated to this event
    * @param value      the payload of the project
    */
  final case class ProjectUpdated(id: String,
                                  uuid: String,
                                  parentUuid: String,
                                  rev: Long,
                                  meta: Meta,
                                  value: ProjectValue)
      extends KafkaEvent

  /**
    * A project that has been deprecated.
    *
    * @param id         the unique identifier of the project
    * @param uuid       the permanent identifier for the project
    * @param parentUuid the permanent identifier for the organization
    * @param rev        the revision number that this event generates
    * @param meta       the metadata associated to this event
    */
  final case class ProjectDeprecated(id: String, uuid: String, parentUuid: String, rev: Long, meta: Meta)
      extends KafkaEvent

  /**
    * @param name the organization name
    */
  final case class OrganizationValue(name: String)

  /**
    * An organization that has been created.
    *
    * @param id         the unique identifier of the organization
    * @param label      the label (segment) of the organization
    * @param uuid       the permanent identifier for the organization
    * @param rev        the revision number that this event generates
    * @param meta       the metadata associated to this event
    * @param value      the payload of the organization
    */
  final case class OrganizationCreated(id: String, label: String, uuid: String, rev: Long, meta: Meta, value: OrganizationValue)
      extends KafkaEvent

  /**
    * An organization that has been updated.
    *
    * @param id         the unique identifier of the organization
    * @param uuid       the permanent identifier for the organization
    * @param rev        the revision number that this event generates
    * @param meta       the metadata associated to this event
    * @param value      the payload of the organization
    */
  final case class OrganizationUpdated(id: String, uuid: String, rev: Long, meta: Meta, value: OrganizationValue)
      extends KafkaEvent

  /**
    * An organization that has been deprecated.
    *
    * @param id         the unique identifier of the organization
    * @param uuid       the permanent identifier for the organization
    * @param rev        the revision number that this event generates
    * @param meta       the metadata associated to this event
    */
  final case class OrganizationDeprecated(id: String, uuid: String, rev: Long, meta: Meta) extends KafkaEvent

  private implicit val config: Configuration = Configuration.default.withDiscriminator("type")

  private implicit val metaDecoder: Decoder[Meta] = deriveDecoder[Meta]

  private implicit val orgValueDecoder: Decoder[OrganizationValue] = deriveDecoder[OrganizationValue]

  private implicit val projectValueDecoder: Decoder[ProjectValue] = Decoder.instance { c =>
    for {
      name <- c.downField(nxv.name.reference.value).as[String]
      lpm  <- c.downField(nxv.prefixMappings.reference.value).as[List[LoosePrefixMapping]]
      mappings = lpm.flatMap(mappingToMapEntry).toMap
      baseString <- c.downField(nxv.base.reference.value).as[String]
      base       <- Iri.absolute(baseString).left.map(err => DecodingFailure(err, c.history))
    } yield ProjectValue(name, base, mappings)
  }

  implicit val kafkaEventDecoder: Decoder[KafkaEvent] = deriveDecoder[KafkaEvent]
}

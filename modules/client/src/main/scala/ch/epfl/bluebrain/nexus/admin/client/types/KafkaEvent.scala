package ch.epfl.bluebrain.nexus.admin.client.types

import ch.epfl.bluebrain.nexus.commons.types.Meta
import ch.epfl.bluebrain.nexus.commons.types.identity.Identity._
import io.circe.{Decoder, Json}
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
  final case class ProjectCreated(id: String, uuid: String, parentUuid: String, rev: Long, meta: Meta, value: Json)
      extends KafkaEvent

  final case class ProjectUpdated(id: String, uuid: String, parentUuid: String, rev: Long, meta: Meta, value: Json)
      extends KafkaEvent

  final case class ProjectDeprecated(id: String, uuid: String, parentUuid: String, rev: Long, meta: Meta)
      extends KafkaEvent

  final case class OrganizationCreated(id: String, uuid: String, rev: Long, meta: Meta, value: Json) extends KafkaEvent

  final case class OrganizationUpdated(id: String, uuid: String, rev: Long, meta: Meta, value: Json) extends KafkaEvent

  final case class OrganizationDeprecated(id: String, uuid: String, rev: Long, meta: Meta) extends KafkaEvent

  private implicit val config: Configuration = Configuration.default.withDiscriminator("type")

  private implicit val metaDecoder: Decoder[Meta] = deriveDecoder[Meta]

  implicit val kafkaEventDecoder: Decoder[KafkaEvent] = deriveDecoder[KafkaEvent]
}

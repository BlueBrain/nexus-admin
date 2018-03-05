package ch.epfl.bluebrain.nexus.admin.core.projects

import ch.epfl.bluebrain.nexus.admin.core.projects.Project.Value
import ch.epfl.bluebrain.nexus.admin.core.types.{Ref, Versioned}
import ch.epfl.bluebrain.nexus.admin.refined.project._
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder, Json}
final case class Project(id: Ref[ProjectReference], rev: Long, value: Value, deprecated: Boolean) extends Versioned

object Project {

  implicit def valueEncoder(implicit E: Encoder[Config] = deriveEncoder[Config]): Encoder[Value] = deriveEncoder[Value]
  implicit def valueDecoder(implicit D: Decoder[Config] = deriveDecoder[Config]): Decoder[Value] = deriveDecoder[Value]

  final case class Value(`@context`: Json, config: Config)
  final case class Config(maxAttachmentSize: Long)
}

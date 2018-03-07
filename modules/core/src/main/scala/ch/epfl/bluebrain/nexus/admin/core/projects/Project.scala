package ch.epfl.bluebrain.nexus.admin.core.projects

import ch.epfl.bluebrain.nexus.admin.core.projects.Project.Value
import ch.epfl.bluebrain.nexus.admin.core.types.{Ref, Versioned}
import ch.epfl.bluebrain.nexus.admin.refined.project._
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder, Json}

/**
  * Data type representing the state of a project.
  *
  * @param id         a identifier for the project
  * @param rev        the selected revision for the project
  * @param value      the metadata of the project
  * @param deprecated the deprecation status of the project
  */
final case class Project(id: Ref[ProjectReference], rev: Long, value: Value, deprecated: Boolean) extends Versioned

object Project {

  implicit def valueEncoder(implicit E: Encoder[Config] = deriveEncoder[Config]): Encoder[Value] = deriveEncoder[Value]
  implicit def valueDecoder(implicit D: Decoder[Config] = deriveDecoder[Config]): Decoder[Value] = deriveDecoder[Value]

  /**
    * Data type representing the payload value of the project
    *
    * @param `@context` the prefix mappings
    * @param config     the configuration of the project
    */
  final case class Value(`@context`: Json, config: Config)

  /**
    * Project configuration
    *
    * @param maxAttachmentSize the maximum attachment file size in bytes
    */
  final case class Config(maxAttachmentSize: Long)
}

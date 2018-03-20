package ch.epfl.bluebrain.nexus.admin.core.projects

import cats.syntax.show._
import ch.epfl.bluebrain.nexus.admin.core.config.AppConfig.ProjectsConfig
import ch.epfl.bluebrain.nexus.admin.core.projects.Project.ProjectValue
import ch.epfl.bluebrain.nexus.admin.core.types.{Ref, Versioned}
import ch.epfl.bluebrain.nexus.admin.core.types.Ref._
import ch.epfl.bluebrain.nexus.admin.ld.Const._
import ch.epfl.bluebrain.nexus.admin.ld.JsonLD._
import ch.epfl.bluebrain.nexus.admin.refined.ld._
import ch.epfl.bluebrain.nexus.admin.refined.project._
import io.circe.generic.semiauto.deriveEncoder
import io.circe.{Decoder, Encoder, Json}
import io.circe.refined._
import io.circe.syntax._

/**
  * Data type representing the state of a project.
  *
  * @param id         a identifier for the project
  * @param rev        the selected revision for the project
  *
  * @param value      the payload of the project
  * @param deprecated the deprecation status of the project
  */
final case class Project(id: Ref[ProjectReference], rev: Long, value: ProjectValue, original: Json, deprecated: Boolean)
    extends Versioned

object Project {

  implicit def projectEncoder(implicit config: ProjectsConfig): Encoder[Project] =
    Encoder.encodeJson.contramap {
      case Project(id, rev, _, json, deprecated) =>
        json deepMerge Json.obj(
          `@id`               -> id.asJson,
          `@type`             -> Json.fromString(nxv.Project.show),
          nxv.rev.show        -> Json.fromLong(rev),
          nxv.deprecated.show -> Json.fromBoolean(deprecated)
        )

    }

  /**
    * Data type representing the payload value of the project
    *
    * @param label          the optionally available label
    * @param description    the optionally available description
    * @param prefixMappings the prefix mappings
    * @param config         the configuration of the project
    */
  final case class ProjectValue(label: Option[String],
                                description: Option[String],
                                prefixMappings: List[LoosePrefixMapping],
                                config: Config)

  object ProjectValue {

    implicit final def projectValueDecoder(implicit config: ProjectsConfig): Decoder[ProjectValue] =
      Decoder.decodeJson.map { json =>
        val jsonLD      = json.appendContext(projectContext)
        val size        = jsonLD.downFirst(nxv.config).value[Int](nxv.attSize).map(_.toLong).getOrElse(config.attachmentSize)
        val label       = jsonLD.value[String](schema.label)
        val description = jsonLD.value[String](nxv.description)
        val list = jsonLD.down(nxv.prefixMappings).foldLeft(List.empty[LoosePrefixMapping]) { (acc, c) =>
          (for {
            prefix <- c.value[Prefix](nxv.prefix)
            ns     <- c.value[AliasOrNamespace](nxv.namespace)
          } yield LoosePrefixMapping(prefix, ns)) match {
            case Some(v) => v :: acc
            case None    => acc
          }

        }
        ProjectValue(label, description, list.reverse, Config(size))
      }

    implicit final def projectValueEncoder(implicit EL: Encoder[LoosePrefixMapping] = deriveEncoder[LoosePrefixMapping],
                                           EC: Encoder[Config] = deriveEncoder[Config]): Encoder[ProjectValue] =
      deriveEncoder[ProjectValue]
  }

  /**
    * A single name to uri mapping (an entry of a prefix mapping). This contains prefix mappings and aliases
    *
    * @param prefix    the prefix (left side of a PrefixMapping)
    * @param namespace the namespace or the alias value (right side of a PrefixMapping)
    */
  final case class LoosePrefixMapping(prefix: Prefix, namespace: AliasOrNamespace)

  /**
    * Project configuration
    *
    * @param maxAttachmentSize the maximum attachment file size in bytes
    */
  final case class Config(maxAttachmentSize: Long)
}

package ch.epfl.bluebrain.nexus.admin.core.projects

import cats.syntax.show._
import ch.epfl.bluebrain.nexus.admin.core.config.AppConfig.ProjectsConfig
import ch.epfl.bluebrain.nexus.admin.core.projects.Project.ProjectValue
import ch.epfl.bluebrain.nexus.admin.core.types.Ref._
import ch.epfl.bluebrain.nexus.admin.core.types.{Ref, Versioned}
import ch.epfl.bluebrain.nexus.admin.ld.Const._
import ch.epfl.bluebrain.nexus.admin.ld.JsonLD
import ch.epfl.bluebrain.nexus.admin.ld.JsonLD._
import ch.epfl.bluebrain.nexus.admin.refined.ld._
import ch.epfl.bluebrain.nexus.admin.refined.project._
import io.circe.generic.semiauto.deriveEncoder
import io.circe.refined._
import io.circe.syntax._
import io.circe.{Decoder, Encoder, Json}

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
    * @param name           the name of the project
    * @param description    the optionally available description
    * @param prefixMappings the prefix mappings
    * @param config         the configuration of the project
    */
  final case class ProjectValue(name: String,
                                description: Option[String],
                                prefixMappings: List[LoosePrefixMapping],
                                config: Config)

  object ProjectValue {

    implicit final def projectValueDecoder(implicit config: ProjectsConfig): Decoder[ProjectValue] = {
      def retrieveLinks(jsonLD: JsonLD): List[LoosePrefixMapping] =
        jsonLD.down(nxv.prefixMappings).foldLeft(List.empty[LoosePrefixMapping]) { (acc, c) =>
          (for {
            prefix <- c.value[Prefix](nxv.prefix)
            ns     <- c.value[AliasOrNamespace](nxv.namespace)
          } yield LoosePrefixMapping(prefix, ns)) match {
            case Some(v) => v :: acc
            case None    => acc
          }
        }
      Decoder.decodeJson.emap { json =>
        val jsonLD = json.appendContext(projectContext)
        val size   = jsonLD.downFirst(nxv.config).value[Int](nxv.attSize).map(_.toLong).getOrElse(config.attachmentSize)
        val name   = jsonLD.value[String](nxv.name)
        val desc   = jsonLD.value[String](nxv.description)
        val list   = retrieveLinks(jsonLD).reverse
        name
          .map(n => ProjectValue(n, desc, list, Config(size)))
          .toRight(s"The '${nxv.name.show}' field is required.")
      }
    }

    implicit final def projectValueEncoder(implicit EL: Encoder[LoosePrefixMapping] = deriveEncoder[LoosePrefixMapping],
                                           EC: Encoder[Config] = deriveEncoder[Config]): Encoder[ProjectValue] =
      deriveEncoder[ProjectValue]
  }

  implicit class PrefixMappingsSyntax(values: List[LoosePrefixMapping]) {

    /**
      * Checks if ''subset'' is a subset of the elements on ''values''.
      *
      * @param subset the list of [[LoosePrefixMapping]]
      * @return true when the ''subset'' is contained on the ''value'' and false otherwise
      */
    def containsMappings(subset: List[LoosePrefixMapping]): Boolean =
      contains(subset.mapped, values.mapped)

    /**
      * Converts the list of pairs (prefix, namespace) into a Map
      */
    def mapped: Map[Prefix, AliasOrNamespace] = values.map { case LoosePrefixMapping(k, v) => k -> v }.toMap

    private def contains[A, B](subset: Map[A, B], map: Map[A, B]): Boolean =
      map.filterKeys(subset.keySet.contains) == subset
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

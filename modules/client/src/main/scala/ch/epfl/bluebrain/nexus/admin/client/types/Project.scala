package ch.epfl.bluebrain.nexus.admin.client.types

import ch.epfl.bluebrain.nexus.admin.client.types.Project._
import ch.epfl.bluebrain.nexus.admin.ld.Const._
import ch.epfl.bluebrain.nexus.admin.refined.ld.{AliasOrNamespace, Prefix}
import ch.epfl.bluebrain.nexus.admin.refined.project.ProjectReference
import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder
import io.circe.refined._

/**
  * Data type exposing project information to external services.
  *
  * @param name           the name of the project
  * @param prefixMappings the prefix mappings
  * @param config         the configuration of the project
  * @param rev            the selected revision for the project
  * @param deprecated     the deprecation status of the project
  */
final case class Project(name: ProjectReference,
                         prefixMappings: List[LoosePrefixMapping],
                         config: Config,
                         rev: Long,
                         deprecated: Boolean)

object Project {

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

  implicit def projectDecoder(implicit
                              DLPM: Decoder[LoosePrefixMapping] = deriveDecoder[LoosePrefixMapping],
                              DC: Decoder[Config] = deriveDecoder[Config]): Decoder[Project] = {
    Decoder.instance { c =>
      for {
        name       <- c.downField(nxv.name.reference.value).as[ProjectReference]
        lpm        <- c.downField(nxv.prefixMappings.reference.value).as[List[LoosePrefixMapping]]
        config     <- c.downField(nxv.config.reference.value).as[Config]
        rev        <- c.downField(nxv.rev.reference.value).as[Long]
        deprecated <- c.downField(nxv.deprecated.reference.value).as[Boolean]
      } yield Project(name, lpm, config, rev, deprecated)
    }
  }
}

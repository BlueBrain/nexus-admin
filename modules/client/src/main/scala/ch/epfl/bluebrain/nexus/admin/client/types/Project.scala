package ch.epfl.bluebrain.nexus.admin.client.types

import ch.epfl.bluebrain.nexus.admin.ld.Const._
import ch.epfl.bluebrain.nexus.admin.refined.ld.{AliasOrNamespace, Prefix}
import ch.epfl.bluebrain.nexus.rdf.Iri
import ch.epfl.bluebrain.nexus.rdf.Iri.AbsoluteIri
import eu.timepit.refined.auto._
import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder
import io.circe.refined._

/**
  * Data type exposing project information to external services.
  *
  * @param name           the name of the project
  * @param prefixMappings the prefix mappings
  * @param base           the base used to generate IDs
  * @param rev            the selected revision for the project
  * @param deprecated     the deprecation status of the project
  * @param uuid           the permanent identifier for the project
  */
final case class Project(name: String,
                         prefixMappings: Map[String, AbsoluteIri],
                         base: String,
                         rev: Long,
                         deprecated: Boolean,
                         uuid: String)

object Project {

  /**
    * A single name to uri mapping (an entry of a prefix mapping). This contains prefix mappings and aliases
    *
    * @param prefix    the prefix (left side of a PrefixMapping)
    * @param namespace the namespace or the alias value (right side of a PrefixMapping)
    */
  final case class LoosePrefixMapping(prefix: Prefix, namespace: AliasOrNamespace)

  private def mappingToMapEntry(mapping: LoosePrefixMapping): Option[(String, AbsoluteIri)] = {
    Iri.absolute(mapping.namespace.value) match {
      case Right(iri) => Some((mapping.prefix, iri))
      case Left(_)    => None
    }

  }

  implicit def projectDecoder(
      implicit
      DLPM: Decoder[LoosePrefixMapping] = deriveDecoder[LoosePrefixMapping]): Decoder[Project] = {
    Decoder.instance { c =>
      for {
        name <- c.downField(nxv.name.reference.value).as[String]
        lpm  <- c.downField(nxv.prefixMappings.reference.value).as[List[LoosePrefixMapping]]
        mappings = lpm.map(mappingToMapEntry).flatten.toMap
        base       <- c.downField(nxv.base.reference.value).as[String]
        rev        <- c.downField(nxv.rev.reference.value).as[Long]
        deprecated <- c.downField(nxv.deprecated.reference.value).as[Boolean]
        uuid       <- c.downField(nxv.uuid.reference.value).as[String]
      } yield Project(name, mappings, base, rev, deprecated, uuid)
    }
  }
}

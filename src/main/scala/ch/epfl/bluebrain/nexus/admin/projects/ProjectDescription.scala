package ch.epfl.bluebrain.nexus.admin.projects

import ch.epfl.bluebrain.nexus.rdf.instances._
import ch.epfl.bluebrain.nexus.rdf.Iri
import ch.epfl.bluebrain.nexus.rdf.Iri.AbsoluteIri
import io.circe.{Decoder, DecodingFailure}

/**
  * Type that represents a project payload for creation and update requests.
  *
  * @param description an optional description
  * @param apiMappings the API mappings
  * @param base        the base IRI for generated resource IDs
  * @param vocabulary  an optional vocabulary for resources with no context
  */
final case class ProjectDescription(description: Option[String],
                                    apiMappings: Map[String, AbsoluteIri],
                                    base: AbsoluteIri,
                                    vocabulary: Option[AbsoluteIri])

object ProjectDescription {

  final case class Mapping(prefix: String, namespace: AbsoluteIri)

  implicit val mappingDecoder: Decoder[Mapping] = Decoder.instance { hc =>
    for {
      prefix    <- hc.downField("prefix").as[String]
      namespace <- hc.downField("namespace").as[String]
      iri       <- Iri.absolute(namespace).left.map(err => DecodingFailure(err, hc.history))
    } yield Mapping(prefix, iri)
  }

  implicit val descriptionDecoder: Decoder[ProjectDescription] = Decoder.instance { hc =>
    for {
      desc <- hc.downField("description").as[Option[String]]
      lam = hc.downField("apiMappings").as[List[Mapping]].getOrElse(List.empty)
      map = lam.map(am => am.prefix -> am.namespace).toMap
      base <- hc.downField("base").as[AbsoluteIri]
      voc  <- hc.downField("vocabulary").as[Option[AbsoluteIri]]
    } yield ProjectDescription(desc, map, base, voc)
  }
}

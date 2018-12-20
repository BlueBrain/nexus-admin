package ch.epfl.bluebrain.nexus.admin.projects

import ch.epfl.bluebrain.nexus.rdf.Iri.AbsoluteIri
import io.circe.{Encoder, Json}

/**
  * Type that represents a project.
  *
  * @param label        the project label (segment)
  * @param organization the organization label
  * @param description  an optional description
  * @param apiMappings  the API mappings
  * @param base         the base IRI for generated resource IDs
  */
final case class Project(label: String,
                         organization: String,
                         description: Option[String],
                         apiMappings: Map[String, AbsoluteIri],
                         base: AbsoluteIri) {

  /**
    * @return full label for the project (including organization).
    */
  def fullLabel: String = s"$organization/$label"
}

object Project {

  implicit val projectEncoder: Encoder[Project] = Encoder.encodeJson.contramap { p =>
    p.description match {
      case Some(desc) =>
        Json.obj(
          "_label"        -> Json.fromString(p.label),
          "_organization" -> Json.fromString(p.organization),
          "description"   -> Json.fromString(desc),
          "apiMappings" -> Json.arr(p.apiMappings.toList.map {
            case (prefix, namespace) =>
              Json.obj("prefix" -> Json.fromString(prefix), "namespace" -> Json.fromString(namespace.asString))
          }: _*),
          "base" -> Json.fromString(p.base.asString)
        )
      case None =>
        Json.obj(
          "_label"        -> Json.fromString(p.label),
          "_organization" -> Json.fromString(p.organization),
          "apiMappings" -> Json.arr(p.apiMappings.toList.map {
            case (prefix, namespace) =>
              Json.obj("prefix" -> Json.fromString(prefix), "namespace" -> Json.fromString(namespace.asString))
          }: _*),
          "base" -> Json.fromString(p.base.asString)
        )
    }
  }
}

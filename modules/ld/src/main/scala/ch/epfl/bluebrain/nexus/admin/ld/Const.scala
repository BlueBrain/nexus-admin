package ch.epfl.bluebrain.nexus.admin.ld

import ch.epfl.bluebrain.nexus.commons.test.Resources
import eu.timepit.refined.auto._
import eu.timepit.refined.string._
import io.circe.Json

// $COVERAGE-OFF$
object Const extends Resources {

  val `@id`      = "@id"
  val `@type`    = "@type"
  val `@context` = "@context"

  private val prefixes: Set[Prefix] = Set(
    Prefix("nxv", nxv.value),
    Prefix("dcterms", "http://purl.org/dc/terms/"),
    Prefix("owl", "http://www.w3.org/2002/07/owl#"),
    Prefix("prov", "http://www.w3.org/ns/prov#"),
    Prefix("rdf", rdf.value),
    Prefix("rdfs", "http://www.w3.org/2000/01/rdf-schema#"),
    Prefix("schema", schema.value),
    Prefix("sh", "http://www.w3.org/ns/shacl#"),
    Prefix("shsh", "http://www.w3.org/ns/shacl-shacl#"),
    Prefix("skos", "http://www.w3.org/2004/02/skos/core#"),
    Prefix("xsd", "http://www.w3.org/2001/XMLSchema#")
  )

  private val assetsContext = JsonLD(jsonContentOf("/default-context.json")).contextValue

  val defaultContext: JsonLD = Json.obj("@context" -> prefixes.foldLeft(assetsContext) {
    case (json, Prefix(pName, pValue)) => json deepMerge Json.obj(pName.value -> Json.fromString(pValue.value))
  })

  //noinspection TypeAnnotation
  object rdf extends IdRefBuilder("rdf", "http://www.w3.org/1999/02/22-rdf-syntax-ns#") {
    val tpe = build("type")
  }

  //noinspection TypeAnnotation
  object schema extends IdRefBuilder("schema", "http://schema.org/") {
    val name = build("name")
  }

  //noinspection TypeAnnotation
  object nxv extends IdRefBuilder("nxv", "https://bbp-nexus.epfl.ch/vocabs/nexus/core/terms/v0.1.0/") {
    val rev               = build("rev")
    val deprecated        = build("deprecated")
    val self              = build("self")
    val Project           = build("Project")
    val maxAttachmentSize = build("maxAttachmentSize")
    val config            = build("config")
    val allFields         = build("all")

  }

}
// $COVERAGE-ON$

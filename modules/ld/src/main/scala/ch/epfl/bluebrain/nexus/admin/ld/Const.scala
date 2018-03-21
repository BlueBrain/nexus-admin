package ch.epfl.bluebrain.nexus.admin.ld

import ch.epfl.bluebrain.nexus.commons.http.ContextUri
import java.util.regex.Pattern.quote
import ch.epfl.bluebrain.nexus.commons.test.Resources
import eu.timepit.refined.auto._
import eu.timepit.refined.string._
import io.circe.Json

// $COVERAGE-OFF$
object Const extends Resources {

  val `@id`      = "@id"
  val `@type`    = "@type"
  val `@context` = "@context"
  val `@value`   = "@value"

  val filterContext: JsonLD = jsonContentOf("/filter-context.json")

  val projectContext: JsonLD = jsonContentOf("/project-context.json")

  val linksContext: ContextUri  = ContextUri("https://bbp.epfl.ch/nexus/v0/contexts/nexus/core/links/v0.1.0")
  val searchContext: ContextUri = ContextUri("https://bbp.epfl.ch/nexus/v0/contexts/nexus/core/search/v0.1.0")

  val projectSchema: Json =
    jsonContentOf("/schemas/nexus/core/project/v0.1.0.json", Map(quote("{{base}}") -> "https://bbp-nexus.epfl.ch"))

  //noinspection TypeAnnotation
  object rdf extends IdRefBuilder("rdf", "http://www.w3.org/1999/02/22-rdf-syntax-ns#") {
    val tpe = build("type")
  }

  object xmlSchema extends IdRefBuilder("xml", "http://www.w3.org/2001/XMLSchema#") {
    val dateTime = build("dateTime")
  }

  //noinspection TypeAnnotation
  object schema extends IdRefBuilder("schema", "http://schema.org/") {
    val name  = build("name")
    val label = build("label")
  }

  //noinspection TypeAnnotation
  object nxv extends IdRefBuilder("nxv", "https://bbp-nexus.epfl.ch/vocabs/nexus/core/terms/v0.1.0/") {
    val name           = build("name")
    val rev            = build("rev")
    val deprecated     = build("deprecated")
    val published      = build("published")
    val self           = build("self")
    val allFields      = build("all")
    val createdAtTime  = build("createdAtTime")
    val updatedAtTime  = build("updatedAtTime")
    val description    = build("description")
    val Project        = build("Project")
    val config         = build("config")
    val attSize        = build("maxAttachmentSize")
    val prefixMappings = build("prefixMappings")
    val prefix         = build("prefix")
    val namespace      = build("namespace")
    val total          = build("total")
    val results        = build("results")
    val links          = build("links")
    val source         = build("source")
    val resultId       = build("resultId")
    val score          = build("score")
  }

}
// $COVERAGE-ON$

package ch.epfl.bluebrain.nexus.admin.ld

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

  /**
    * Terms used in the select block in SPARQL queries.
    */
  object SelectTerms {
    val score    = "score"
    val rank     = "rank"
    val subject  = "s"
    val total    = "total"
    val maxScore = "maxscore"
  }

  val filterContext: JsonLD = jsonContentOf("/filter-context.json")

  val projectContext: JsonLD = jsonContentOf("/project-context.json")

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
    val id             = build("_id")
    val rev            = build("_rev")
    val deprecated     = build("_deprecated")
    val published      = build("_published")
    val self           = build("_self")
    val next           = build("_next")
    val previous       = build("_previous")
    val allFields      = build("_all")
    val createdAtTime  = build("_createdAtTime")
    val updatedAtTime  = build("_updatedAtTime")
    val description    = build("description")
    val Project        = build("Project")
    val config         = build("config")
    val attSize        = build("maxAttachmentSize")
    val prefixMappings = build("prefixMappings")
    val prefix         = build("prefix")
    val namespace      = build("namespace")
    val total          = build("_total")
    val results        = build("_results")
    val links          = build("_links")
    val source         = build("_source")
    val score          = build("_score")
    val maxScore       = build("_maxScore")
    val from           = build("_from")
    val size           = build("_size")
  }

  //noinspection TypeAnnotation
  object bds extends IdRefBuilder("bds", "http://www.bigdata.com/rdf/search#") {
    val search    = build("search")
    val relevance = build("relevance")
    val rank      = build("rank")
  }

}
// $COVERAGE-ON$

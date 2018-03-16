package ch.epfl.bluebrain.nexus.admin.ld

import ch.epfl.bluebrain.nexus.commons.test.Resources
import eu.timepit.refined.auto._
import eu.timepit.refined.string._

// $COVERAGE-OFF$
object Const extends Resources {

  val `@id`      = "@id"
  val `@type`    = "@type"
  val `@context` = "@context"

  val filterContext: JsonLD = jsonContentOf("/filter-context.json")

  val projectContext: JsonLD = jsonContentOf("/project-context.json")

  //noinspection TypeAnnotation
  object rdf extends IdRefBuilder("rdf", "http://www.w3.org/1999/02/22-rdf-syntax-ns#") {
    val tpe = build("type")
  }

  //noinspection TypeAnnotation
  object schema extends IdRefBuilder("schema", "http://schema.org/") {
    val name  = build("name")
    val label = build("label")
  }

  //noinspection TypeAnnotation
  object nxv extends IdRefBuilder("nxv", "https://bbp-nexus.epfl.ch/vocabs/nexus/core/terms/v0.1.0/") {
    val rev            = build("rev")
    val deprecated     = build("deprecated")
    val self           = build("self")
    val allFields      = build("all")
    val createdAtTime  = build("createdAtTime")
    val description    = build("description")
    val Project        = build("Project")
    val config         = build("config")
    val attSize        = build("maxAttachmentSize")
    val prefixMappings = build("prefixMappings")
    val prefix         = build("prefix")
    val namespace      = build("namespace")
  }

}
// $COVERAGE-ON$

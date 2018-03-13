package ch.epfl.bluebrain.nexus.admin.ld

import eu.timepit.refined.auto._
import eu.timepit.refined.string._

// $COVERAGE-OFF$
object Const {

  val `@id`      = "@id"
  val `@type`    = "@type"
  val `@context` = "@context"

  //noinspection TypeAnnotation
  object rdf extends IdRefBuilder("rdf", "http://www.w3.org/1999/02/22-rdf-syntax-ns#") {
    val tpe = build("type")
  }

  //noinspection TypeAnnotation
  object nxv extends IdRefBuilder("nxv", "https://bbp-nexus.epfl.ch/vocabs/nexus/core/terms/v0.1.0/") {
    val rev               = build("rev")
    val deprecated        = build("deprecated")
    val self              = build("self")
    val Project           = build("Project")
    val maxAttachmentSize = build("maxAttachmentSize")
    val config            = build("config")
    val allFields            = build("all")

  }

}
// $COVERAGE-ON$

package ch.epfl.bluebrain.nexus.admin.config

import ch.epfl.bluebrain.nexus.rdf.Iri.AbsoluteIri
import ch.epfl.bluebrain.nexus.rdf.syntax.node.unsafe._

object Contexts {
  val base: AbsoluteIri = url"https://bluebrain.github.io/nexus/contexts/".value

  val resourceCtxUri: AbsoluteIri = base + "resource.json"
  val adminCtxUri: AbsoluteIri    = base + "admin.json"
  val errorCtxUri: AbsoluteIri    = base + "error.json"
}

package ch.epfl.bluebrain.nexus.admin.config

import ch.epfl.bluebrain.nexus.rdf.Iri.AbsoluteIri
import ch.epfl.bluebrain.nexus.rdf.syntax.node.unsafe._

object Schemas {
  val base: AbsoluteIri = url"https://bluebrain.github.io/nexus/schemas/".value

  val organizationSchemaUri: AbsoluteIri = base + "organization.json"
  val projectSchemaUri: AbsoluteIri      = base + "project.json"
}

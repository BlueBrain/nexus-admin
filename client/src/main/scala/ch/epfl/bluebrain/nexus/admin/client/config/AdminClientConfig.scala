package ch.epfl.bluebrain.nexus.admin.client.config

import ch.epfl.bluebrain.nexus.rdf.Iri.AbsoluteIri

final case class AdminClientConfig(prefix: String, publicIri: AbsoluteIri)

package ch.epfl.bluebrain.nexus.admin.client.config

import ch.epfl.bluebrain.nexus.rdf.Iri.AbsoluteIri

/**
  * Configuration for [[ch.epfl.bluebrain.nexus.admin.client.AdminClient]].
  *
  * @param baseIri base URL for all the HTTP calls, including prefix.
  */
final case class AdminClientConfig(baseIri: AbsoluteIri)

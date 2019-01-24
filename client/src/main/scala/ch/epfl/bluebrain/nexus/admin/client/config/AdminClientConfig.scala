package ch.epfl.bluebrain.nexus.admin.client.config

import ch.epfl.bluebrain.nexus.rdf.Iri.AbsoluteIri

/**
  * Configuration for [[ch.epfl.bluebrain.nexus.admin.client.AdminClient]].
  *
  * @param publicIri    base URL for all the project and organization ids, including prefix
  * @param internalIri  base URL for all the HTTP calls, including prefix
  */
final case class AdminClientConfig(publicIri: AbsoluteIri, internalIri: AbsoluteIri) {
  lazy val projectsIri: AbsoluteIri = internalIri + "projects"
  lazy val orgsIri: AbsoluteIri     = internalIri + "orgs"
}

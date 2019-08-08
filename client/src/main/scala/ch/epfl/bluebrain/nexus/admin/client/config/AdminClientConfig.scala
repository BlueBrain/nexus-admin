package ch.epfl.bluebrain.nexus.admin.client.config

import ch.epfl.bluebrain.nexus.rdf.Iri.AbsoluteIri

import scala.concurrent.duration._

/**
  * Configuration for [[ch.epfl.bluebrain.nexus.admin.client.AdminClient]].
  *
  * @param publicIri     base URL for all the project and organization ids, including prefix
  * @param internalIri   base URL for all the HTTP calls, including prefix
  * @param sseRetryDelay delay for retrying after completion on SSE. 1 second by default.
  */
final case class AdminClientConfig(
    publicIri: AbsoluteIri,
    internalIri: AbsoluteIri,
    sseRetryDelay: FiniteDuration = 1 second
) {
  lazy val projectsIri: AbsoluteIri = internalIri + "projects"
  lazy val orgsIri: AbsoluteIri     = internalIri + "orgs"
}

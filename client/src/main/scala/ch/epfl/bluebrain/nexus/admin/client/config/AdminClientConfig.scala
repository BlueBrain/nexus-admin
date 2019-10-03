package ch.epfl.bluebrain.nexus.admin.client.config

import ch.epfl.bluebrain.nexus.rdf.Iri.AbsoluteIri

import scala.concurrent.duration._

/**
  * Configuration for [[ch.epfl.bluebrain.nexus.admin.client.AdminClient]].
  *
  * @param publicIri     base URL for all the project and organization ids, excluding prefix
  * @param internalIri   base URL for all the HTTP calls, excluding prefix
  * @param prefix        the prefix
  * @param sseRetryDelay delay for retrying after completion on SSE. 1 second by default.
  */
final case class AdminClientConfig(
    publicIri: AbsoluteIri,
    internalIri: AbsoluteIri,
    prefix: String,
    sseRetryDelay: FiniteDuration = 1 second
) {
  private lazy val baseInternalIri  = internalIri + prefix
  lazy val projectsIri: AbsoluteIri = baseInternalIri + "projects"
  lazy val orgsIri: AbsoluteIri     = baseInternalIri + "orgs"
  lazy val eventsIri: AbsoluteIri   = baseInternalIri + "events"
}

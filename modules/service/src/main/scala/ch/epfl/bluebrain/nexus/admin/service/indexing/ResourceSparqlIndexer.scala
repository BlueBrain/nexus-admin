package ch.epfl.bluebrain.nexus.admin.service.indexing

import ch.epfl.bluebrain.nexus.admin.core.resources.ResourceEvent
import ch.epfl.bluebrain.nexus.admin.core.resources.ResourceEvent.{ResourceCreated, ResourceDeprecated, ResourceUpdated}
import ch.epfl.bluebrain.nexus.admin.ld.Const._
import ch.epfl.bluebrain.nexus.admin.ld.JsonLdOps._
import ch.epfl.bluebrain.nexus.admin.refined.ld.Id
import ch.epfl.bluebrain.nexus.commons.iam.acls.Meta
import ch.epfl.bluebrain.nexus.commons.sparql.client.{PatchStrategy, SparqlClient}
import io.circe.Json

/**
  * Indexer which takes a resource event and calls SPARQL client with relevant update
  * @param client SPARQL client
  */
class ResourceSparqlIndexer[F[_]](client: SparqlClient[F]) {

  /**
    * Index resource event using SPARQL client
    * @param event event to index
    * @return successful future if indexing succeeded or a failed one if there was an exception
    */
  final def index(event: ResourceEvent): F[Unit] = event match {
    case ResourceCreated(id, rev, meta, _, value) =>
      val createdAt = Json.obj(nxv.createdAtTime.value -> meta.instant.jsonLd)
      val data      = value deepMerge buildMeta(id, rev, meta) deepMerge createdAt deepMerge projectContext
      client.replace(id.value, data)
    case ResourceUpdated(id, rev, meta, _, value) =>
      val data = value deepMerge buildMeta(id, rev, meta) deepMerge projectContext
      client.patch(id.value, data, PatchStrategy.removeButPredicates(Set(nxv.createdAtTime.value)))
    case ResourceDeprecated(id, rev, meta, _) =>
      val deprecated = Json.obj(nxv.deprecated.value -> Json.fromBoolean(true))
      val data       = buildMeta(id, rev, meta) deepMerge deprecated
      val strategy   = PatchStrategy.removePredicates(Set(nxv.deprecated.value, nxv.updatedAtTime.value, nxv.rev.value))
      client.patch(id.value, data, strategy)
  }

  private def buildMeta(id: Id, rev: Long, meta: Meta, deprecated: Boolean = false) = {
    Json.obj(
      `@id`                   -> Json.fromString(id.value),
      nxv.deprecated.value    -> Json.fromBoolean(deprecated),
      nxv.rev.value           -> Json.fromLong(rev),
      nxv.updatedAtTime.value -> meta.instant.jsonLd,
      rdf.tpe.value           -> nxv.Project.id.jsonLd
    )

  }

}

object ResourceSparqlIndexer {

  def apply[F[_]](client: SparqlClient[F]): ResourceSparqlIndexer[F] =
    new ResourceSparqlIndexer(client)
}
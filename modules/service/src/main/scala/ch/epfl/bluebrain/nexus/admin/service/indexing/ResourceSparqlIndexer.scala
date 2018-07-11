package ch.epfl.bluebrain.nexus.admin.service.indexing

import ch.epfl.bluebrain.nexus.admin.core.resources.ResourceEvent
import ch.epfl.bluebrain.nexus.admin.core.resources.ResourceEvent.{ResourceCreated, ResourceDeprecated, ResourceUpdated}
import ch.epfl.bluebrain.nexus.admin.ld.Const._
import ch.epfl.bluebrain.nexus.admin.ld.JsonLdOps._
import ch.epfl.bluebrain.nexus.admin.refined.ld.Id
import ch.epfl.bluebrain.nexus.commons.types.Meta
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
    case ResourceCreated(id, label, uuid, _, rev, meta, _, value) =>
      val createdAt = Json.obj(nxv.createdAtTime.value -> meta.instant.jsonLd)
      val data      = value deepMerge buildMeta(id, uuid, rev, meta, label = Some(label)) deepMerge createdAt deepMerge resourceContext
      client.replace(id.value, data)
    case ResourceUpdated(id, label, uuid, _, rev, meta, _, value) =>
      val data = value deepMerge buildMeta(id, uuid, rev, meta, label = Some(label)) deepMerge resourceContext
      client.patch(id.value, data, PatchStrategy.removeButPredicates(Set(nxv.createdAtTime.value, nxv.label.value)))
    case ResourceDeprecated(id, uuid, _, rev, meta, _) =>
      val deprecated = Json.obj(nxv.deprecated.value -> Json.fromBoolean(true))
      val data       = buildMeta(id, uuid, rev, meta) deepMerge deprecated
      val strategy   = PatchStrategy.removePredicates(Set(nxv.deprecated.value, nxv.updatedAtTime.value, nxv.rev.value))
      client.patch(id.value, data, strategy)
  }

  private def buildMeta(id: Id,
                        uuid: String,
                        rev: Long,
                        meta: Meta,
                        deprecated: Boolean = false,
                        label: Option[String] = None) = {
    val json = Json.obj(
      `@id`                   -> Json.fromString(id.value),
      nxv.deprecated.value    -> Json.fromBoolean(deprecated),
      nxv.rev.value           -> Json.fromLong(rev),
      nxv.updatedAtTime.value -> meta.instant.jsonLd,
      rdf.tpe.value           -> nxv.Project.id.jsonLd,
      nxv.uuid.value          -> Json.fromString(uuid)
    )
    label.map(l => json deepMerge Json.obj(nxv.label.value -> Json.fromString(l))).getOrElse(json)
  }

}

object ResourceSparqlIndexer {

  def apply[F[_]](client: SparqlClient[F]): ResourceSparqlIndexer[F] =
    new ResourceSparqlIndexer(client)
}

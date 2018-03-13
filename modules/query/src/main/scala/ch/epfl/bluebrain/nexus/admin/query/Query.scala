package ch.epfl.bluebrain.nexus.admin.query

import ch.epfl.bluebrain.nexus.admin.query.Query._
import ch.epfl.bluebrain.nexus.admin.refined.queries._
import ch.epfl.bluebrain.nexus.commons.iam.acls.Path
import ch.epfl.bluebrain.nexus.commons.types.search.SortList
import io.circe.Json

/**
  * Data type representing the current query.
  *
  * @param id    the unique identifier for the query
  * @param path  the path where the query will take effect
  * @param value the query payload
  */
final case class Query(id: QueryId, path: Path, value: QueryPayload)

object Query {

  /**
    * Data type representing the current query payload.
    *
    * @param `@context` the optionally available JSON-LD @context
    * @param q          the optionally available full text search term used
    * @param deprecated the optionally available deprecated field
    * @param published  the optionally available published field
    * @param format     the optionally available format to output the query response
    * @param resource   the resources which the query is going to trigger
    * @param fields     the fields the query is going to display on the response
    * @param sort       the ordering the query is going to display on the response
    */
  final case class QueryPayload(`@context`: Json = Json.obj(),
                                q: Option[String] = None,
                                deprecated: Option[Boolean] = None,
                                published: Option[Boolean] = None,
                                format: JsonLdFormat = JsonLdFormat.Default,
                                resource: QueryResource = QueryResource.Instances,
                                fields: Set[Field] = Set.empty,
                                sort: SortList = SortList.Empty)

  final def apply(path: Path, value: QueryPayload): Query =
    new Query(QueryId(randomQueryName()), path, value)
}

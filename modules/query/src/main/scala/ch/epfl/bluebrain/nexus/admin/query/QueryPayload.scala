package ch.epfl.bluebrain.nexus.admin.query

import ch.epfl.bluebrain.nexus.admin.query.filtering.Filter
import ch.epfl.bluebrain.nexus.commons.types.search.SortList
import io.circe.Json

/**
  * Data type representing the current query payload.
  *
  * @param `@context` the optionally available JSON-LD @context
  * @param filter     the filter
  * @param q          the optionally available full text search term used
  * @param deprecated the optionally available deprecated field
  * @param published  the optionally available published field
  * @param format     the optionally available format to output the query response
  * @param resource   the resources which the query is going to trigger
  * @param fields     the fields the query is going to display on the response
  * @param sort       the ordering the query is going to display on the response
  */
final case class QueryPayload(`@context`: Json = Json.obj(),
                              filter: Filter = Filter.Empty,
                              q: Option[String] = None,
                              deprecated: Option[Boolean] = None,
                              published: Option[Boolean] = None,
                              format: JsonLdFormat = JsonLdFormat.Default,
                              resource: QueryResource = QueryResource.Instances,
                              fields: Set[Field] = Set.empty,
                              sort: SortList = SortList.Empty)

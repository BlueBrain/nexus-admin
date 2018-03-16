package ch.epfl.bluebrain.nexus.admin.query

import ch.epfl.bluebrain.nexus.admin.ld.{Const, JsonLD}
import ch.epfl.bluebrain.nexus.admin.query.filtering.Filter
import ch.epfl.bluebrain.nexus.commons.types.search.{Sort, SortList}
import io.circe.{Decoder, Json}

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

object QueryPayload {

  final def queryPayloadDecoders(
      customerContext: JsonLD): (Decoder[Filter], Decoder[Field], Decoder[SortList], Decoder[QueryPayload]) = {
    val ctx: JsonLD        = customerContext.appendContext(Const.filterContext)
    implicit val filterDec = Filter.filterDecoder(ctx)

    implicit val fieldDecoder: Decoder[Field] =
      Decoder.decodeString.emap { field =>
        ctx.expand(field).map(Field.apply).toRight(s"Could not expand field '$field'")
      }

    implicit val sortDecoder: Decoder[Sort] =
      Decoder.decodeString.map(Sort(_)).emap {
        case Sort(order, value) =>
          ctx.expand(value).map(id => Sort(order, id.value)).toRight(s"Could not expand field '$value'")
      }

    implicit val sortlistDecoder: Decoder[SortList] =
      Decoder.decodeList[Sort].map(list => SortList(list))

    val queryPayloadDecoder = Decoder.instance { hc =>
      for {
        filter     <- hc.downField("filter").as[Option[Filter]].map(_ getOrElse Filter.Empty)
        q          <- hc.downField("q").as[Option[String]].map(_ orElse None)
        deprecated <- hc.downField("deprecated").as[Option[Boolean]].map(_ orElse None)
        published  <- hc.downField("published").as[Option[Boolean]].map(_ orElse None)
        format     <- hc.downField("format").as[Option[JsonLdFormat]].map(_ getOrElse JsonLdFormat.Default)
        resource   <- hc.downField("resource").as[Option[QueryResource]].map(_ getOrElse QueryResource.Instances)
        fields     <- hc.downField("fields").as[Option[Set[Field]]].map(_ getOrElse Set.empty[Field])
        sort       <- hc.downField("sort").as[Option[SortList]].map(_ getOrElse SortList.Empty)

      } yield (QueryPayload(ctx.contextValue, filter, q, deprecated, published, format, resource, fields, sort))
    }

    (filterDec, fieldDecoder, sortlistDecoder, queryPayloadDecoder)
  }
}

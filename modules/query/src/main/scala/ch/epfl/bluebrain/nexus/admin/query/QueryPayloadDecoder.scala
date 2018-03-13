package ch.epfl.bluebrain.nexus.admin.query

import ch.epfl.bluebrain.nexus.admin.ld.{Const, JsonLD}
import ch.epfl.bluebrain.nexus.admin.query.JsonLdFormat._
import ch.epfl.bluebrain.nexus.admin.query.QueryResource._
import ch.epfl.bluebrain.nexus.admin.query.filtering.Filter
import ch.epfl.bluebrain.nexus.commons.types.search.{Sort, SortList}
import io.circe.{Decoder, Json}

/**
  * Provides the decoder for [[QueryPayload]] and all the composed decoders.
  *
  * @param ctx the context applied for [[QueryPayload]]
  */
private[query] class QueryPayloadDecoder(ctx: JsonLD) {

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

  final implicit val decodeQuery: Decoder[QueryPayload] =
    Decoder.instance { hc =>
      for {
        filter     <- hc.downField("filter").as[Option[Filter]].map(_ getOrElse Filter.Empty)
        q          <- hc.downField("q").as[Option[String]].map(_ orElse None)
        deprecated <- hc.downField("deprecated").as[Option[Boolean]].map(_ orElse None)
        published  <- hc.downField("published").as[Option[Boolean]].map(_ orElse None)
        format     <- hc.downField("format").as[Option[JsonLdFormat]].map(_ getOrElse JsonLdFormat.Default)
        resource   <- hc.downField("resource").as[Option[QueryResource]].map(_ getOrElse QueryResource.Instances)
        fields     <- hc.downField("fields").as[Option[Set[Field]]].map(_ getOrElse Set.empty[Field])
        sort       <- hc.downField("sort").as[Option[SortList]].map(_ getOrElse SortList.Empty)

      } yield
        (QueryPayload(ctx.hcursor.get[Json]("@context").getOrElse(Json.obj()),
                      filter,
                      q,
                      deprecated,
                      published,
                      format,
                      resource,
                      fields,
                      sort))
    }
}

object QueryPayloadDecoder {

  /**
    * Construct a [[QueryPayloadDecoder]]
    *
    * @param ctx the provided context
    */
  final def apply(ctx: JsonLD): QueryPayloadDecoder =
    new QueryPayloadDecoder(Json.obj("@context" -> (ctx.contextValue deepMerge Const.defaultContext.contextValue)))

}

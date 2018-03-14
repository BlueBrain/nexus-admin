package ch.epfl.bluebrain.nexus.admin.core.directives

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{Directive, Directive1, MalformedQueryParamRejection}
import ch.epfl.bluebrain.nexus.admin.core.config.AppConfig.PaginationConfig
import ch.epfl.bluebrain.nexus.admin.core.directives.StringUnmarshaller._
import ch.epfl.bluebrain.nexus.admin.ld.{Const, JsonLD}
import ch.epfl.bluebrain.nexus.admin.ld.JsonLD._
import ch.epfl.bluebrain.nexus.admin.query.QueryPayload._
import ch.epfl.bluebrain.nexus.admin.query.filtering.Filter
import ch.epfl.bluebrain.nexus.admin.query.{Field, JsonLdFormat, QueryPayload}
import ch.epfl.bluebrain.nexus.commons.types.HttpRejection.WrongOrInvalidJson
import ch.epfl.bluebrain.nexus.commons.types.search.{Pagination, SortList}
import io.circe.parser._
import io.circe.{Decoder, Json}

import scala.util.Try

/**
  * Collection of query specific directives.
  */
trait QueryDirectives {

  /**
    * Extracts pagination specific query params from the request or defaults to the preconfigured values.
    *
    * @param qs the preconfigured query settings
    */
  def paginated(implicit qs: PaginationConfig): Directive1[Pagination] =
    (parameter('from.as[Int] ? qs.pagination.from) & parameter('size.as[Int] ? qs.pagination.size)).tmap {
      case (from, size) => Pagination(from.max(0), size.max(0).min(qs.maxSize.value))
    }

  /**
    * Extracts the ''context'' query param from the request.
    */
  def context: Directive1[Json] =
    parameter('context.as[String] ? Json.obj().noSpaces).flatMap { contextParam =>
      parse(contextParam) match {
        case Right(ctxJson) => provide(Json.obj("@context" -> (ctxJson deepMerge Const.defaultContext.contextValue)))
        case Left(_) =>
          reject(
            MalformedQueryParamRejection("context",
                                         "IllegalContext",
                                         Some(WrongOrInvalidJson(Some("The context must be a valid JSON")))))
      }
    }

  /**
    * Extracts the ''filter'' query param from the request.
    *
    */
  def filtered(implicit D: Decoder[Filter]): Directive1[Filter] =
    parameter('filter.as[Filter](unmarshaller(toJson)) ? Filter.Empty)

  /**
    * Extracts the ''q'' query param from the request. This param will be used as a full text search
    */
  def q: Directive1[Option[String]] =
    parameter('q.?)

  /**
    * Extracts the ''deprecated'' query param from the request.
    */
  def deprecated: Directive1[Option[Boolean]] =
    parameter('deprecated.as[Boolean].?)

  /**
    * Extracts the ''published'' query param from the request.
    */
  def published: Directive1[Option[Boolean]] =
    parameter('published.as[Boolean].?)

  /**
    * Extracts the ''format'' query param from the request.
    */
  def format(implicit D: Decoder[JsonLdFormat]): Directive1[JsonLdFormat] =
    parameter('format.as[JsonLdFormat](unmarshaller(toJsonString)) ? (JsonLdFormat.Default: JsonLdFormat))

  /**
    * Extracts the ''sort'' query param from the request.
    */
  def sort(implicit D: Decoder[SortList]): Directive1[SortList] =
    parameter('sort.as[SortList](unmarshaller(toJsonArr)) ? SortList.Empty)

  /**
    * Extracts the ''fields'' query param from the request.
    */
  def fields(implicit D: Decoder[Set[Field]]): Directive1[Set[Field]] = {
    parameter('fields.as[Set[Field]](unmarshaller(toJsonArr)) ? Set.empty[Field])
  }

  private def toJsonArr(value: String) =
    Right(Json.arr(value.split(",").foldLeft(Vector.empty[Json])((acc, c) => acc :+ Json.fromString(c)): _*))
  private def toJsonString(value: String) = Right(Json.fromString(value))
  private def toJson(value: String)       = parse(value).left.map(err => WrongOrInvalidJson(Try(err.message).toOption))

  /**
    * Extracts the [[QueryPayload]] from the provided query parameters.
    */
  def paramsToQuery(implicit qs: PaginationConfig): Directive[(Pagination, QueryPayload)] =
    context.flatMap { jsonCtx =>
      implicit val (filterDec, fieldsDec, sortListDec, _) = queryPayloadDecoders(jsonCtx)

      (filtered & q & deprecated & published & fields & sort & format & paginated).tmap {
        case (filter, query, deprecate, publish, fieldSet, sortList, format, page) =>
          (page,
           QueryPayload(`@context` = JsonLD(jsonCtx).contextValue,
                        filter = filter,
                        q = query,
                        deprecated = deprecate,
                        published = publish,
                        fields = fieldSet,
                        sort = sortList,
                        format = format))
      }
    }
}

object QueryDirectives extends QueryDirectives

package ch.epfl.bluebrain.nexus.admin.query

import ch.epfl.bluebrain.nexus.admin.ld.{Const, JsonLD}
import ch.epfl.bluebrain.nexus.admin.ld.Const.nxv
import ch.epfl.bluebrain.nexus.admin.query.filtering.Expr.ComparisonExpr
import ch.epfl.bluebrain.nexus.admin.query.filtering.Filter
import ch.epfl.bluebrain.nexus.admin.query.filtering.Op._
import ch.epfl.bluebrain.nexus.admin.query.filtering.PropPath.UriPath
import ch.epfl.bluebrain.nexus.admin.query.filtering.Term.LiteralTerm
import ch.epfl.bluebrain.nexus.commons.test.Resources
import ch.epfl.bluebrain.nexus.commons.types.search.{Sort, SortList}
import io.circe.Json
import org.scalatest.{Inspectors, Matchers, WordSpecLike}

class QueryPayloadDecoderSpec extends WordSpecLike with Matchers with Resources with Inspectors {

  "A QueryPayload" should {

    val queryJson       = jsonContentOf("/query/query-full-text.json")
    val queryFilterJson = jsonContentOf("/query/query-filter-text.json")
    val filter = Filter(
      ComparisonExpr(Eq,
                     UriPath(s"http://www.w3.org/ns/prov#startedAtTime"),
                     LiteralTerm(""""2017-10-07T16:00:00-05:00"""")))
    val list = List(
      queryJson -> QueryPayload(
        q = Some("someText"),
        resource = QueryResource.Schemas,
        deprecated = Some(false),
        published = Some(false),
        format = JsonLdFormat.Expanded,
        fields = Set(Field(nxv.allFields.id)),
        sort = SortList(List(Sort(s"-${nxv.value}createdAtTime")))
      ),
      queryFilterJson -> QueryPayload(
        `@context` = Json.obj("some" -> Json.fromString("http://example.com/prov#")),
        filter = filter,
        deprecated = Some(true),
        published = Some(true),
        format = JsonLdFormat.Compacted,
        sort = SortList(List(Sort(s"-http://example.com/prov#createdAtTime")))
      )
    )
    "be decoded properly from json" in {
      forAll(list) {
        case (json, model) =>
          val ctx      = JsonLD(json).contextValue
          val decoders = QueryPayloadDecoder(Json.obj("@context" -> ctx))
          import decoders._
          json.as[QueryPayload] shouldEqual Right(model.copy(`@context` = ctx deepMerge Const.defaultContext.contextValue))
      }
    }
  }

}

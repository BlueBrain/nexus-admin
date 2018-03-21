package ch.epfl.bluebrain.nexus.admin.service.encoders

import akka.http.scaladsl.model.Uri
import ch.epfl.bluebrain.nexus.admin.ld.Const.nxv
import ch.epfl.bluebrain.nexus.admin.refined.ld.Id
import ch.epfl.bluebrain.nexus.commons.http.ContextUri
import ch.epfl.bluebrain.nexus.commons.http.JsonOps.JsonOpsSyntax
import ch.epfl.bluebrain.nexus.commons.types.search.QueryResult.{ScoredQueryResult, UnscoredQueryResult}
import io.circe.syntax._
import io.circe.{Encoder, Json}
import ch.epfl.bluebrain.nexus.admin.ld.JsonLdOps._
import ch.epfl.bluebrain.nexus.admin.service.types.Links
import ch.epfl.bluebrain.nexus.commons.http.JsonOps._

/**
  * Constructs implicit encoders used to format HTTP responses.
  * @param coreContext core context to inject into the response
  * @param extractId   function which extracts Id from Entity
  */
class RoutesEncoder[Entity](coreContext: ContextUri)(implicit extractId: (Entity) => Id) {

  implicit val linksEncoder: Encoder[Links] =
    Encoder.encodeJson.contramap { links =>
      links.values.mapValues {
        case href :: Nil => Json.fromString(s"$href")
        case hrefs       => Json.arr(hrefs.map(href => Json.fromString(s"$href")): _*)
      }.asJson
    }

  implicit val queryResultEncoder: Encoder[UnscoredQueryResult[Id]] =
    Encoder.encodeJson.contramap { qr =>
      Json.obj(
        nxv.source.reference.value -> qr.source.jsonLd,
        nxv.links.reference.value  -> linksEncoder(selfLink(qr.source))
      )
    }

  implicit val scoredQueryResultEncoder: Encoder[ScoredQueryResult[Id]] =
    Encoder.encodeJson.contramap { qr =>
      Json.obj(
        nxv.score.reference.value  -> Json.fromFloatOrString(qr.score),
        nxv.source.reference.value -> qr.source.jsonLd,
        nxv.links.reference.value  -> linksEncoder(selfLink(qr.source))
      )
    }

  implicit def queryResultEntityEncoder(implicit E: Encoder[Entity]): Encoder[UnscoredQueryResult[Entity]] =
    Encoder.encodeJson.contramap { qr =>
      Json.obj(
        nxv.source.reference.value -> E(qr.source).addContext(coreContext),
        nxv.links.reference.value  -> linksEncoder(selfLink(qr.source))
      )
    }

  implicit def scoredQueryResultEntityEncoder(implicit E: Encoder[Entity]): Encoder[ScoredQueryResult[Entity]] =
    Encoder.encodeJson.contramap { qr =>
      Json.obj(
        nxv.score.reference.value  -> Json.fromFloatOrString(qr.score),
        nxv.source.reference.value -> E(qr.source).addContext(coreContext),
        nxv.links.reference.value  -> linksEncoder(selfLink(qr.source))
      )
    }

  protected def selfLink(id: Id): Links = Links("self" -> Uri(id.value))
}

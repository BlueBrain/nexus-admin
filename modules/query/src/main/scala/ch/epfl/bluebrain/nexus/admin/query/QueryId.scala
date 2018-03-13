package ch.epfl.bluebrain.nexus.admin.query

import cats.Show
import ch.epfl.bluebrain.nexus.admin.refined.queries.QueryName
import eu.timepit.refined.api.RefType.applyRef
import io.circe.{Decoder, Encoder}

/**
  * Unique query identifier
  *
  * @param id the unique query identifier
  */
final case class QueryId(id: QueryName)

object QueryId {

  final implicit def queryIdShow: Show[QueryId] =
    Show.show(id => s"${id.id}")

  final implicit def queryIdIdEncoder(implicit S: Show[QueryId]): Encoder[QueryId] =
    Encoder.encodeString.contramap(id => S.show(id))

  final implicit val queryIdDecoder: Decoder[QueryId] =
    Decoder.decodeString.emap(str => applyRef[QueryName](str).map(QueryId.apply))

}

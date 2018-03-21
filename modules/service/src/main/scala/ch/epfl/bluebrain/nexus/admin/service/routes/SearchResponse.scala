package ch.epfl.bluebrain.nexus.admin.service.routes

import akka.http.scaladsl.model.{StatusCodes, Uri}
import akka.http.scaladsl.server.Directives.{complete, extract, onSuccess}
import akka.http.scaladsl.server.Route
import cats.syntax.functor._
import ch.epfl.bluebrain.nexus.admin.query.Field
import ch.epfl.bluebrain.nexus.admin.service.types.Links
import ch.epfl.bluebrain.nexus.admin.service.query.LinksQueryResults
import ch.epfl.bluebrain.nexus.commons.http.ContextUri
import ch.epfl.bluebrain.nexus.commons.http.JsonLdCirceSupport._
import ch.epfl.bluebrain.nexus.commons.types.search.QueryResult.{ScoredQueryResult, UnscoredQueryResult}
import ch.epfl.bluebrain.nexus.commons.types.search.{Pagination, QueryResult, QueryResults}
import io.circe.Encoder

import scala.concurrent.{ExecutionContext, Future}

trait SearchResponse {

  /**
    * Syntactic sugar for constructing a [[Route]] from the [[QueryResults]]
    */
  implicit class QueryResultsOpts[Id](qr: Future[QueryResults[Id]]) {

    private[routes] def addPagination(base: Uri, pagination: Pagination)(implicit
                                                                         R: Encoder[UnscoredQueryResult[Id]],
                                                                         S: Encoder[ScoredQueryResult[Id]],
                                                                         L: Encoder[Links],
                                                                         orderedKeys: OrderedKeys): Route = {
      implicit val context: ContextUri = ContextUri(
        "https://bbp-nexus.epfl.ch/staging/v0/contexts/nexus/core/search/v0.1.0")

      extract(_.request.uri) { uri =>
        onSuccess(qr) { result =>
          val lqu = base.copy(path = uri.path, fragment = uri.fragment, rawQueryString = uri.rawQueryString)
          complete(StatusCodes.OK -> LinksQueryResults(result, pagination, lqu))
        }
      }
    }

    /**
      * Interface syntax to expose new functionality into [[QueryResults]] response type.
      * Decides if either a generic type ''Id'' or ''Entity'' should be used.
      *
      * @param fields     the fields query parameters
      * @param base       the service public uri + prefix
      * @param pagination the pagination values
      */
    @SuppressWarnings(Array("MaxParameters"))
    def buildResponse[Entity](fields: Set[Field], base: Uri, pagination: Pagination)(
        implicit
        f: Id => Future[Option[Entity]],
        ec: ExecutionContext,
        R: Encoder[UnscoredQueryResult[Id]],
        S: Encoder[ScoredQueryResult[Id]],
        Re: Encoder[UnscoredQueryResult[Entity]],
        Se: Encoder[ScoredQueryResult[Entity]],
        L: Encoder[Links],
        orderedKeys: OrderedKeys): Route = {
      if (fields.contains(Field.All)) {
        qr.flatMap { q =>
            q.results
              .foldLeft(Future(List.empty[QueryResult[Entity]])) { (acc, current) =>
                f(current.source) flatMap {
                  case Some(instance) => acc.map(list => current.map(_ => instance) :: list)
                  case None           => acc
                }
              }
              .map(list => q.copyWith(list.reverse))
          }
          .addPagination(base, pagination)
      } else {
        qr.addPagination(base, pagination)
      }
    }
  }

}

object SearchResponse extends SearchResponse

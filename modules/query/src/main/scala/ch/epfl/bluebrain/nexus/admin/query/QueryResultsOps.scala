package ch.epfl.bluebrain.nexus.admin.query

import cats.MonadError
import cats.syntax.all._
import ch.epfl.bluebrain.nexus.admin.ld.Const.SelectTerms
import ch.epfl.bluebrain.nexus.commons.types.search.QueryResult.{ScoredQueryResult, UnscoredQueryResult}
import ch.epfl.bluebrain.nexus.commons.types.search.{QueryResult, QueryResults}
import org.apache.jena.query.{QuerySolution, ResultSet}
import ch.epfl.bluebrain.nexus.admin.refined.ld.Id

import scala.collection.JavaConverters._
import eu.timepit.refined.api.RefType.applyRef

import scala.util.Try

object QueryResultsOps {

  /**
    * Transforms F[ResultSet] to F[QueryResults]
    * @param rsF ResultSet to transform
    * @return F[QueryResult] created from rsF
    */
  def rsToQueryResults[F[_]](rsF: F[ResultSet], scored: Boolean)(
      implicit F: MonadError[F, Throwable]): F[QueryResults[Id]] = {

    def scoredQueryResult(sol: QuerySolution): (Option[QueryResult[Id]], Option[Long], Option[Float]) = {
      val queryResult = for {
        (subj, score) <- subjectScoreFrom(sol)
        id            <- applyRef[Id](subj).toOption
      } yield ScoredQueryResult(score, id)
      (queryResult, totalFrom(sol), maxScoreFrom(sol))
    }

    def unscoredQueryResult(sol: QuerySolution): (Option[QueryResult[Id]], Option[Long], Option[Float]) = {
      val queryResult = subjectFrom(sol).flatMap(applyRef[Id](_).toOption).map(UnscoredQueryResult(_))
      (queryResult, totalFrom(sol), None)
    }

    def buildQueryResults(scoredResponse: Boolean, listWithTotal: (Vector[QueryResult[Id]], Long, Float)) = {
      val (vector, total, maxScore) = listWithTotal
      if (scoredResponse) QueryResults[Id](total, maxScore, vector.toList)
      else QueryResults[Id](total, vector.toList)
    }

    rsF.map({ rs: ResultSet =>
      val listWithTotal = rs.asScala.foldLeft[(Vector[QueryResult[Id]], Long, Float)]((Vector.empty, 0L, 0F)) {
        case ((queryResults, currentTotal, currentMaxScore), sol) =>
          val (qr, total, maxScore) =
            if (scored) scoredQueryResult(sol)
            else unscoredQueryResult(sol)
          (qr.map(queryResults :+ _).getOrElse(queryResults),
           total.getOrElse(currentTotal),
           maxScore.getOrElse(currentMaxScore))
      }
      buildQueryResults(scored, listWithTotal)
    })

  }

  private def subjectScoreFrom(qs: QuerySolution): Option[(String, Float)] =
    for {
      score   <- scoreFrom(qs)
      subject <- subjectFrom(qs)
    } yield subject -> score

  private def scoreFrom(qs: QuerySolution): Option[Float] =
    Try(qs.get(s"?${SelectTerms.score}").asLiteral().getLexicalForm.toFloat).toOption

  private def totalFrom(qs: QuerySolution): Option[Long] =
    Try(qs.get(s"?${SelectTerms.total}").asLiteral().getLexicalForm.toLong).toOption

  private def subjectFrom(qs: QuerySolution): Option[String] =
    Try(qs.get(s"?${SelectTerms.subject}").asResource().getURI).toOption

  private def maxScoreFrom(qs: QuerySolution): Option[Float] =
    Try(qs.get(s"?${SelectTerms.maxScore}").asLiteral().getLexicalForm.toFloat).toOption

}

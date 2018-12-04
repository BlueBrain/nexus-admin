package ch.epfl.bluebrain.nexus.admin.directives

import akka.http.javadsl.server.Rejections.validationRejection
import akka.http.scaladsl.server.Directive1
import akka.http.scaladsl.server.Directives._
import ch.epfl.bluebrain.nexus.admin.config.AppConfig.PaginationConfig
import ch.epfl.bluebrain.nexus.commons.types.search.Pagination
import ch.epfl.bluebrain.nexus.rdf.Iri.Path
import ch.epfl.bluebrain.nexus.rdf.syntax.akka._

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
    (parameter('from.as[Int] ? qs.default.from) & parameter('size.as[Int] ? qs.default.size)).tmap {
      case (from, size) => Pagination(from.max(0), size.max(0).min(qs.maxSize))
    }

  /**
    * Extracts the ''deprecated'' query param from the request.
    */
  def deprecated: Directive1[Option[Boolean]] =
    parameter('deprecated.as[Boolean].?)

  /**
    * Extracts the [[Path]] from the unmatched segments
    */
  def extractResourcePath: Directive1[Path] = extractUnmatchedPath.flatMap { path =>
    path.toIriPath match {
      case p if p.asString.contains("//") =>
        reject(validationRejection(s"Path '${p.asString}' cannot contain double slash."))
      case p =>
        provide(p)
    }
  }

  /**
    * Extracts the organization label from the resource segment.
    */
  def extractOrg(resource: Path): Directive1[String] = resource.lastSegment match {
    case Some(segment) => provide(segment)
    case None => reject(validationRejection("Organization path cannot be empty."))
  }

  /**
    * Extracts the organization and project labels from the resource segments.
    */
  def extractProject(resource: Path): Directive1[(String, String)] =
    resource.segments.reverse match {
      case project :: org :: _ => provide((org, project))
      case _ => reject(validationRejection(s"Path '${resource.asString}' is not a valid project reference."))
  }

}

object QueryDirectives extends QueryDirectives

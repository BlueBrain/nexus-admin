package ch.epfl.bluebrain.nexus.admin.directives

import akka.http.scaladsl.server.Directive1
import akka.http.scaladsl.server.Directives._
import ch.epfl.bluebrain.nexus.admin.config.AppConfig.PaginationConfig
import ch.epfl.bluebrain.nexus.commons.search.Pagination

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
}

object QueryDirectives extends QueryDirectives

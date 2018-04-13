package ch.epfl.bluebrain.nexus.admin.query

import akka.http.scaladsl.model.Uri
import ch.epfl.bluebrain.nexus.commons.types.search.Pagination

final case class QuerySettings(pagination: Pagination, maxSize: Int, base: Uri)

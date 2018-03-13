package ch.epfl.bluebrain.nexus.admin.refined

import java.util.UUID

import eu.timepit.refined.api.RefType.refinedRefType
import eu.timepit.refined.api.Refined
import eu.timepit.refined.string.Uuid

object queries {

  /**
    * Refined type for query names.
    */
  type QueryName = String Refined Uuid

  def randomQueryName(): QueryName = refinedRefType.unsafeWrap(UUID.randomUUID().toString.toLowerCase)

}

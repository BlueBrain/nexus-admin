package ch.epfl.bluebrain.nexus.admin.ld

import ch.epfl.bluebrain.nexus.admin.refined.ld.DecomposableId
import ch.epfl.bluebrain.nexus.admin.refined.ld.DecomposableUri.decompose
import ch.epfl.bluebrain.nexus.admin.ld.Prefix.randomPrefixName

object DecomposableIdOps {

  /**
    * Syntax sugar to expose methods on type [[DecomposableId]]
    */
  implicit class DecomposableIdSyntax(value: DecomposableId) {

    /**
      * Converts a [[DecomposableId]] to an [[IdRef]] with a random ''prefixName''
      */
    def toId: IdRef = {
      val (prefixValue, reference) = decompose(value)
      IdRef(randomPrefixName(), prefixValue, reference)
    }

  }

}

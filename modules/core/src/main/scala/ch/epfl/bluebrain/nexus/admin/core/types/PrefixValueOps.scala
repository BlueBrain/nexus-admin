package ch.epfl.bluebrain.nexus.admin.core.types

import akka.http.scaladsl.model.Uri
import ch.epfl.bluebrain.nexus.admin.refined.ld.PrefixValue

object PrefixValueOps {

  /**
    * Interface syntax to expose new functionality into [[PrefixValue]] type
    *
    * @param prefixVal the instance of [[PrefixValue]]
    */
  implicit class PrefixValueSyntax(prefixVal: PrefixValue) {

    /**
      * Converts [[PrefixValue]] to [[Uri]]
      */
    lazy val toUri: Uri = Uri(prefixVal.value)

    /**
      * Returns a String representation of the uri's address.
      */
    lazy val host: String = toUri.authority.host.address()
  }

}

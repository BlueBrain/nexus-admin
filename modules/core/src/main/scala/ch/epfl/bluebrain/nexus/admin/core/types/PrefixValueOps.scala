package ch.epfl.bluebrain.nexus.admin.core.types

import akka.http.scaladsl.model.Uri
import ch.epfl.bluebrain.nexus.admin.refined.ld.Namespace

object PrefixValueOps {

  /**
    * Interface syntax to expose new functionality into [[Namespace]] type
    *
    * @param prefixVal the instance of [[Namespace]]
    */
  implicit class PrefixValueSyntax(prefixVal: Namespace) {

    /**
      * Converts [[Namespace]] to [[Uri]]
      */
    lazy val toUri: Uri = Uri(prefixVal.value)

    /**
      * Returns a String representation of the uri's address.
      */
    lazy val host: String = toUri.authority.host.address()
  }

}

package ch.epfl.bluebrain.nexus.admin.core.types

import akka.http.scaladsl.model.Uri
import ch.epfl.bluebrain.nexus.admin.refined.ld.Namespace

object NamespaceOps {

  /**
    * Interface syntax to expose new functionality into [[Namespace]] type
    *
    * @param namespace the instance of [[Namespace]]
    */
  implicit class NamespaceSyntax(namespace: Namespace) {

    /**
      * Converts [[Namespace]] to [[Uri]]
      */
    lazy val toUri: Uri = Uri(namespace.value)

    /**
      * Returns a String representation of the uri's address.
      */
    lazy val host: String = toUri.authority.host.address()
  }

}

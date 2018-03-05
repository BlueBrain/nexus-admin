package ch.epfl.bluebrain.nexus.admin.core.types

import akka.http.scaladsl.model.Uri
import ch.epfl.bluebrain.nexus.admin.refined.ld.PrefixValue

object PrefixValueOps {

  implicit class PrefixValueSyntax(a: PrefixValue) {
    private lazy val toUri: Uri = Uri(a.value)
    lazy val host: String       = toUri.authority.host.address()
  }

}

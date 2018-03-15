package ch.epfl.bluebrain.nexus.admin.ld

import cats.Show
import ch.epfl.bluebrain.nexus.admin.refined.ld.{Prefix, Reference}

/**
  * A curie representation as defined in https://www.w3.org/TR/curie/.
  *
  * @param prefix    the prefix component of the curie
  * @param reference the reference component of the curie
  */
final case class Curie(prefix: Prefix, reference: Reference)

object Curie {
  implicit val curieShow: Show[Curie] = Show.show { case Curie(p, r) => s"$p:$r" }
}

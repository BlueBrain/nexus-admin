package ch.epfl.bluebrain.nexus.admin.ld

import cats.Show
import cats.syntax.show._
import ch.epfl.bluebrain.nexus.admin.refined.ld.{Id, PrefixName, PrefixValue, Reference}
import eu.timepit.refined.api.RefType._

/**
  * Data type which contains the information needed to build
  * both a [[Curie]] and a [[Prefix]] as well as to build the expanded [[Id]]
  *
  * @param prefixName  the prefix name
  * @param prefixValue the value to which the name expands,
  *                    as well as the prefix component of the curie
  * @param reference   the reference component of the curie
  */
final case class IdRef(prefixName: PrefixName, prefixValue: PrefixValue, reference: Reference) {

  lazy val curie: Curie =
    Curie(prefixName, reference)

  lazy val prefix: Prefix =
    Prefix(prefixName, prefixValue)

  lazy val id: Id =
    refinedRefType.unsafeWrap(s"${prefixValue.value}${reference.value}")
}

object IdRef {

  final implicit val idRefShow: Show[IdRef] =
    Show.show(id => id.curie.show)

}

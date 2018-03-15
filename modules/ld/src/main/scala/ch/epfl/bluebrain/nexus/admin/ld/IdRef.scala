package ch.epfl.bluebrain.nexus.admin.ld

import cats.Show
import cats.syntax.all._
import cats.instances.all._
import ch.epfl.bluebrain.nexus.admin.refined.ld._
import eu.timepit.refined.api.RefType._

/**
  * Data type which contains the information needed to build
  * both a [[Curie]] and a [[PrefixMapping]] as well as to build the expanded [[Id]]
  *
  * @param prefix    the prefix name
  * @param namespace the value to which the name expands,
  *                  as well as the prefix component of the curie
  * @param reference the reference component of the curie
  */
final case class IdRef(prefix: Prefix, namespace: Namespace, reference: Reference) {

  lazy val curie: Curie =
    Curie(prefix, reference)

  lazy val prefixMapping: PrefixMapping =
    PrefixMapping(prefix, namespace)

  lazy val id: Id =
    refinedRefType.unsafeWrap(s"${namespace.value}${reference.value}")
}

object IdRef {

  /**
    * Attempts to construct a [[IdRef]] from an untyped ''prefixName'', ''prefixValue'' and ''reference''
    *
    * @param prefixName  the untyped prefix name
    * @param prefixValue the untyped value to which the name expands,
    *                    as well as the prefix component of the curie
    * @param reference   the untyped reference component of the curie
    */
  // $COVERAGE-OFF$
  final def build(prefixName: String, prefixValue: String, reference: String): Either[String, IdRef] =
    (applyRef[Prefix](prefixName), applyRef[Namespace](prefixValue), applyRef[Reference](reference)).mapN {
      case ((p, v, r)) => new IdRef(p, v, r)
    }
  // $COVERAGE-ON$

  final implicit val idRefShow: Show[IdRef] =
    Show.show(id => id.curie.show)

}

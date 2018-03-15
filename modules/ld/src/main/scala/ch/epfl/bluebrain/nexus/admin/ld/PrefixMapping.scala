package ch.epfl.bluebrain.nexus.admin.ld

import ch.epfl.bluebrain.nexus.admin.refined.ld.{Prefix, Namespace}
import ch.epfl.bluebrain.nexus.commons.test.Randomness._
import eu.timepit.refined.api.RefType.refinedRefType
import eu.timepit.refined.api.Refined
import eu.timepit.refined.numeric.Greater
import eu.timepit.refined._
import eu.timepit.refined.auto._

/**
  * A single name to uri mapping (an entry of a prefix mapping).
  *
  * @param prefix    the prefix (left side of a PrefixMapping)
  * @param namespace the namespace (right side of a PrefixMapping).
  *                  Is the value to which the name expands, as well as the prefix component of the curie.
  */
final case class PrefixMapping(prefix: Prefix, namespace: Namespace)
object PrefixMapping {
  private val startPool = Vector.range('a', 'z') ++ Vector.range('A', 'Z') ++ Vector('_')
  private val pool      = Vector.range('a', 'z') ++ Vector.range('A', 'Z') ++ Vector.range('0', '9') :+ '_' :+ '-' :+ '.'

  /**
    * Creates a random [[Prefix]].
    *
    * @param length the length of the prefix (left side of a PrefixMapping) to be created
    */
  def randomPrefix(length: Int Refined Greater[W.`1`.T] = 5): Prefix =
    refinedRefType.unsafeWrap(genString(1, startPool) + genString(genInt(length - 1), pool))

}

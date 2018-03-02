package ch.epfl.bluebrain.nexus.admin.ld

import ch.epfl.bluebrain.nexus.admin.refined.ld.{PrefixName, PrefixValue}
import ch.epfl.bluebrain.nexus.commons.test.Randomness._
import eu.timepit.refined.api.RefType.refinedRefType

/**
  * A single name to uri mapping (an entry of a prefix mapping).
  *
  * @param name  the prefix name
  * @param value the value to which the name expands
  */
final case class Prefix(name: PrefixName, value: PrefixValue)
object Prefix {

  /**
    * Creates a random [[PrefixName]].
    *
    * @param length the length of the prefix name to be created
    */
  def randomPrefixName(length: Int = 5): PrefixName = {
    val startPool = Vector.range('a', 'z') ++ Vector.range('A', 'Z') ++ Vector('_')
    val pool      = Vector.range('a', 'z') ++ Vector.range('A', 'Z') ++ Vector.range('0', '9') :+ '_' :+ '-' :+ '.'
    refinedRefType.unsafeWrap(genString(1, startPool) + genString(genInt(length - 1), pool))
  }
}

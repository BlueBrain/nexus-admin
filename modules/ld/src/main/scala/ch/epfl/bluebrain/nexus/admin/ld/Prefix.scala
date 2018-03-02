package ch.epfl.bluebrain.nexus.admin.ld

import ch.epfl.bluebrain.nexus.admin.refined.ld.{PrefixName, PrefixValue}
import ch.epfl.bluebrain.nexus.commons.test.Randomness._
import eu.timepit.refined.api.RefType.refinedRefType
import eu.timepit.refined.api.Refined
import eu.timepit.refined.numeric.Greater
import eu.timepit.refined._
import eu.timepit.refined.auto._

/**
  * A single name to uri mapping (an entry of a prefix mapping).
  *
  * @param name  the prefix name
  * @param value the value to which the name expands
  */
final case class Prefix(name: PrefixName, value: PrefixValue)
object Prefix {
  private val startPool = Vector.range('a', 'z') ++ Vector.range('A', 'Z') ++ Vector('_')
  private val pool      = Vector.range('a', 'z') ++ Vector.range('A', 'Z') ++ Vector.range('0', '9') :+ '_' :+ '-' :+ '.'

  /**
    * Creates a random [[PrefixName]].
    *
    * @param length the length of the prefix name to be created
    */
  def randomPrefixName(length: Int Refined Greater[W.`1`.T] = 1): PrefixName =
    refinedRefType.unsafeWrap(genString(1, startPool) + genString(genInt(length - 1), pool))

}

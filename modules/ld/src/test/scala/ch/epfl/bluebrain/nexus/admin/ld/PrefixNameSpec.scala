package ch.epfl.bluebrain.nexus.admin.ld

import ch.epfl.bluebrain.nexus.admin.refined.ld._
import ch.epfl.bluebrain.nexus.commons.test.Randomness
import eu.timepit.refined.api.RefType._
import org.scalatest.{EitherValues, Inspectors, Matchers, WordSpecLike}
import ch.epfl.bluebrain.nexus.admin.ld.Prefix.randomPrefixName
class PrefixNameSpec extends WordSpecLike with Matchers with Inspectors with Randomness with EitherValues {

  "A PrefixName" should {
    "be constructed correctly" in {
      List.fill(100)(randomPrefixName())
    }
    "fail to construct" in {
      val incorrect = List("", "0ab", "-ab", ".a", "aksjd*&^%")
      forAll(incorrect) { el =>
        val name = applyRef[PrefixName](el)
        name.left.value
      }
    }
  }
}

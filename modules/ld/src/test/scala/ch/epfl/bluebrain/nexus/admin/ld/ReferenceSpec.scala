package ch.epfl.bluebrain.nexus.admin.ld

import ch.epfl.bluebrain.nexus.admin.refined.ld._
import ch.epfl.bluebrain.nexus.commons.test.Randomness
import eu.timepit.refined.api.RefType._
import org.scalatest.{EitherValues, Inspectors, Matchers, WordSpecLike}

class ReferenceSpec extends WordSpecLike with Matchers with Inspectors with Randomness with EitherValues {

  "A Reference" should {
    "be constructed properly" in {
      val correct = List(
        "?asdasd",
        "?a=d",
        "?a=b&c=d#ads",
        "#asd",
        "#as;d"
      )
      forAll(correct) { el =>
        val value = applyRef[Reference](el)
        value.right.value.value shouldEqual el
      }
    }
    "fail to construct" in {
      val incorrect = List(
        "*&^%",
        "%"
      )
      forAll(incorrect) { el =>
        val value = applyRef[Reference](el)
        value.left.value
      }
    }
  }
}

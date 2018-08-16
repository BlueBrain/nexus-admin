package ch.epfl.bluebrain.nexus.admin.refined

import ch.epfl.bluebrain.nexus.admin.refined.ld._
import ch.epfl.bluebrain.nexus.commons.test.Randomness
import eu.timepit.refined.api.RefType._
import org.scalatest.{EitherValues, Inspectors, Matchers, WordSpecLike}

class NamespaceSpec extends WordSpecLike with Matchers with Inspectors with Randomness with EitherValues {

  "A Namespace" should {
    "be constructed properly" in {
      val pool = Vector.range('a', 'z') ++ Vector.range('A', 'Z') ++ Vector.range('0', '9') ++ Vector('-', '_')
      val list =
        Vector.fill(20)(s"https://${genString(5, pool)}.local/${genString(5, pool)}/${genString(5, pool)}/") ++
          Vector.fill(20)(s"https://${genString(5, pool)}.local/${genString(5, pool)}/${genString(5, pool)}#") ++
          Vector.fill(20)(s"urn:${genString(5, pool).toLowerCase}:${genString(5, pool)}:${genString(5, pool)}/") ++
          Vector.fill(20)(s"urn:${genString(5, pool).toLowerCase}:${genString(5, pool)}:${genString(5, pool)}#")
      forAll(list) { el =>
        val value = applyRef[Namespace](el)
        value.right.value.value shouldEqual el
      }

    }
    "fail to construct" in {
      val incorrect = List(
        "../a",
        "a/b/c#",
        "a/b/c/",
        ""
      )
      forAll(incorrect) { el =>
        val value = applyRef[Namespace](el)
        value.left.value
      }
    }
  }
}

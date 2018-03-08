package ch.epfl.bluebrain.nexus.admin.refined

import ch.epfl.bluebrain.nexus.admin.refined.ld._
import ch.epfl.bluebrain.nexus.admin.refined.ld.DecomposableUri._
import ch.epfl.bluebrain.nexus.commons.test.Randomness
import eu.timepit.refined.api.RefType.applyRef
import eu.timepit.refined.auto._
import org.scalatest.{EitherValues, Inspectors, Matchers, WordSpecLike}

class DecomposableIdSpec extends WordSpecLike with Matchers with Randomness with Inspectors with EitherValues {
  "A DecomposableId" should {
    val pool = Vector.range('a', 'z') ++ Vector.range('A', 'Z') ++ Vector.range('0', '9') ++ Vector('-', '_')

    "be constructed properly" in {
      val list =
        Vector.fill(100)(
          s"https://${genString(5, pool)}.local/${genString(5, pool)}${genString(5, pool)}#${genString(5, pool)}")
      forAll(list) { el =>
        val value = applyRef[DecomposableId](el)
        value.right.value.value shouldEqual el
      }

    }
    "fail to construct" in {
      val incorrect = List(
        "../a",
        "a/b/c#",
        "a/b/c/",
        "a/b/c/?asdas",
        "#$%^&*()",
        ""
      ) ++ List.fill(100)(
        s"https://${genString(5, pool)}.local/${genString(5, pool)}/") ++ List
        .fill(100)(s"https://${genString(5, pool)}.local/${genString(5, pool)}/${genString(5, pool)}#") ++ List
        .fill(100)(s"https://${genString(5, pool)}.local/${genString(5, pool)}?${genString(5, pool)}")

      forAll(incorrect) { el =>
        val value = applyRef[DecomposableId](el)
        value.left.value
      }
    }

    "be constructed with inference" in {
      val prefixValue: PrefixValue = "http://example.com/one/two/"
      val reference: Reference = "three"
      val id: DecomposableId = (prefixValue, reference).decomposableId
      id shouldEqual ("http://example.com/one/two/three": DecomposableId)
    }
  }
}

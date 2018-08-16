package ch.epfl.bluebrain.nexus.admin.refined

import ch.epfl.bluebrain.nexus.admin.refined.ld._
import ch.epfl.bluebrain.nexus.admin.refined.ld.Uri._
import ch.epfl.bluebrain.nexus.commons.test.Randomness
import eu.timepit.refined.api.RefType.applyRef
import eu.timepit.refined.auto._
import org.scalatest.{EitherValues, Inspectors, Matchers, WordSpecLike}

class IdSpec extends WordSpecLike with Matchers with Randomness with Inspectors with EitherValues {
  "A Id" should {
    val pool = Vector.range('a', 'z') ++ Vector.range('A', 'Z') ++ Vector.range('0', '9') ++ Vector('-', '_')

    "be constructed properly" in {
      val list =
        Vector.fill(100)(
          s"https://${genString(5, pool)}.local/${genString(5, pool)}${genString(5, pool)}#${genString(5, pool)}")
      forAll(list) { el =>
        val value = applyRef[Id](el)
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
      )

      forAll(incorrect) { el =>
        val value = applyRef[Id](el)
        value.left.value
      }
    }

    "be constructed with inference" in {
      val namespace: Namespace = "http://example.com/one/two/"
      val reference: Reference = "three"
      val id: Id               = (namespace, reference).id
      id shouldEqual ("http://example.com/one/two/three": Id)
      id shouldEqual ("http://example.com/one/two/three": AliasOrNamespace)

    }
  }
}

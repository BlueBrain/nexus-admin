package ch.epfl.bluebrain.nexus.admin.ld

import ch.epfl.bluebrain.nexus.admin.refined.ld._
import ch.epfl.bluebrain.nexus.commons.test.Randomness
import eu.timepit.refined.api.RefType.applyRef
import org.scalatest.{EitherValues, Inspectors, Matchers, WordSpecLike}
import eu.timepit.refined.auto._
import ch.epfl.bluebrain.nexus.admin.ld.IdOps._
class IdSpec extends WordSpecLike with Matchers with Inspectors with Randomness with EitherValues {

  "A Id" should {
    val pool = Vector.range('a', 'z') ++ Vector.range('A', 'Z') ++ Vector.range('0', '9') ++ Vector('-', '_')
    "be constructed properly" in {
      val list =
        Vector.fill(1)(
          s"https://${genString(5, pool)}.local/${genString(5, pool)}/${genString(5, pool)}#${genString(5, pool)}") ++
          Vector.fill(1)(s"https://${genString(5, pool)}.local/${genString(5, pool)}/${genString(5, pool)}")

      forAll(list) { el =>
        val value = applyRef[Id](el)
        value.right.value.value shouldEqual el
      }
    }

    "convert Id to IdRef" in {
      val id: Id       = "https://example.com/some/path"
      val idRef: IdRef = id.toId
      idRef.namespace shouldEqual ("https://example.com/some/": Namespace)
      idRef.reference shouldEqual ("path": Reference)
    }

    "fail to construct" in {
      val incorrect = List(
        "../a",
        "a/b/c#",
        "a/b/c/",
        "a/b/c/?asdas",
        "#$%^&*()",
        ""
      ) ++
        List.fill(100)(s"https://${genString(5, pool)}.local/${genString(5, pool)}?${genString(5, pool)}") ++
        List.fill(100)(s"https://${genString(5, pool)}.local/${genString(5, pool)}/${genString(5, pool)}#") ++
        List.fill(100)(s"https://${genString(5, pool)}.local/${genString(5, pool)}/${genString(5, pool)}/") ++
        List.fill(100)(s"https://${genString(5, pool)}.local")

      forAll(incorrect) { el =>
        val value = applyRef[Id](el)
        value.left.value
      }
    }

  }
}

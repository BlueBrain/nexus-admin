package ch.epfl.bluebrain.nexus.admin.query

import cats.syntax.show._
import ch.epfl.bluebrain.nexus.admin.ld.Const._
import org.scalatest.{Inspectors, Matchers, WordSpecLike}

import scala.util.Left

class FieldSpec extends WordSpecLike with Matchers with Inspectors {
  "A Field" should {
    "be constructed properly from string" in {
      forAll(List(nxv.rev.id, nxv.deprecated.id, nxv.allFields.id)) { field =>
        Field.fromString(field.value) shouldEqual Right(Field(field))
      }
    }

    "be converted into string" in {
      Field(nxv.rev.id).show shouldEqual nxv.rev.id.toString()
    }

    "fail to construct" in {
      forAll(List("asd", "/", "/asd", "asd/", "asd/ads/asd")) { str =>
        Field.fromString(str) shouldBe a[Left[_, _]]
      }
    }
  }
}

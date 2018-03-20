package ch.epfl.bluebrain.nexus.admin.query

import ch.epfl.bluebrain.nexus.admin.query.filtering.Op._
import org.scalatest.{Inspectors, Matchers, OptionValues, WordSpecLike}

class OpSpec extends WordSpecLike with Matchers with Inspectors with OptionValues {

  "A LogicalOp" should {
    "be constructed properly" in {
      forAll(List("and" -> And, "or" -> Or, "not" -> Not, "xor" -> Xor)) {
        case (str, v) =>
          LogicalOp.fromString(str).value shouldEqual v

      }
    }

    "fail to be constructed " in {
      forAll(List("", "whatever")) { str =>
        LogicalOp.fromString(str) shouldEqual None

      }
    }
  }

  "A ComparisonOp" should {
    "be constructed properly" in {
      forAll(List("eq" -> Eq, "ne" -> Ne, "lt" -> Lt, "lte" -> Lte, "gte" -> Gte)) {
        case (str, v) =>
          ComparisonOp.fromString(str).value shouldEqual v

      }
    }

    "fail to be constructed " in {
      forAll(List("", "whatever")) { str =>
        ComparisonOp.fromString(str) shouldEqual None

      }
    }
  }

  "A In Op" should {
    "be constructed properly" in {
      In.fromString("in").value shouldEqual In
    }

    "fail to be constructed " in {
      forAll(List("", "whatever")) { str =>
        In.fromString(str) shouldEqual None

      }
    }
  }
}

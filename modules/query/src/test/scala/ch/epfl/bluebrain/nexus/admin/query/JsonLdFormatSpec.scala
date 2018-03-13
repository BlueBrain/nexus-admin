package ch.epfl.bluebrain.nexus.admin.query

import ch.epfl.bluebrain.nexus.admin.query.JsonLdFormat._
import io.circe.parser.decode
import io.circe.syntax._
import org.scalatest.{Inspectors, Matchers, OptionValues, WordSpecLike}

import scala.util.Left

class JsonLdFormatSpec extends WordSpecLike with Matchers with Inspectors with OptionValues {

  "A JsonLdFormat" should {

    "be constructed properly" in {
      forAll(List[(String, JsonLdFormat)]("compacted" -> Compacted, "expanded" -> Expanded, "flattened" -> Flattened, "default" -> Default)) {
        case (name, v) =>
          JsonLdFormat.fromString(name).value shouldEqual v

      }
    }
    "be encoded properly into json" in {
      forAll(List[(String, JsonLdFormat)]("compacted" -> Compacted, "expanded" -> Expanded, "flattened" -> Flattened, "default" -> Default)) {
        case (name, v) =>
          v.asJson.noSpaces shouldEqual s""""$name""""

      }
    }

    "be decoded properly from json" in {
      forAll(List[(String, JsonLdFormat)]("compacted" -> Compacted, "expanded" -> Expanded, "flattened" -> Flattened, "default" -> Default)) {
        case (name, v) =>
          decode[JsonLdFormat](s""""${name}"""") shouldEqual Right(v)
      }
    }

    "fail to decode" in {
      forAll(List("asd", "")) { str =>
        decode[JsonLdFormat](s""""$str"""") shouldBe a[Left[_, _]]
      }
    }
  }
}

package ch.epfl.bluebrain.nexus.admin.query

import ch.epfl.bluebrain.nexus.admin.query.QueryResource._
import io.circe.parser.decode
import org.scalatest.{Inspectors, Matchers, WordSpecLike}

import scala.util.Left

class QueryResourceSpec extends WordSpecLike with Matchers with Inspectors {

  "A QueryResource" should {

    "be decoded properly from json" in {
      forAll(List("projects" -> Projects, "instances" -> Instances, "schemas" -> Schemas, "contexts" -> Contexts)) { case (str, v) =>
        decode[QueryResource](s""""$str"""") shouldEqual Right(v)

      }
    }

    "fail to decode" in {
      forAll(List("asd", "")) { str =>
        decode[QueryResource](s""""$str"""") shouldBe a[Left[_, _]]
      }
    }
  }
}

package ch.epfl.bluebrain.nexus.admin.query

import cats.syntax.show._
import ch.epfl.bluebrain.nexus.admin.refined.queries._
import io.circe.parser.decode
import org.scalatest.{Inspectors, Matchers, WordSpecLike}
import io.circe.syntax._

import scala.util.Left

class QueryIdSpec extends WordSpecLike with Matchers with Inspectors {
  "A QueryId" should {
    "be encoded properly into json" in {
      val name = randomQueryName()
      QueryId(name).asJson.noSpaces shouldEqual s""""${name.value}""""
    }

    "be decoded properly from json" in {
      val name = randomQueryName()
      decode[QueryId](s""""${name.value}"""") shouldEqual Right(QueryId(name))
    }

    "be converted into string" in {
      val name = randomQueryName()
      QueryId(name).show shouldEqual name.value
    }

    "fail to decode" in {
      forAll(List("asd", "/", "/asd", "asd/", "asd/ads/asd")) { str =>
        decode[QueryId](s""""$str"""") shouldBe a[Left[_, _]]
      }
    }
  }
}

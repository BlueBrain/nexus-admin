package ch.epfl.bluebrain.nexus.admin.refined

import ch.epfl.bluebrain.nexus.admin.refined.queries._
import ch.epfl.bluebrain.nexus.commons.test.Randomness
import eu.timepit.refined.api.RefType.applyRef
import org.scalatest.{EitherValues, Inspectors, Matchers, WordSpecLike}

class QueryNameSpec extends WordSpecLike with Matchers with Randomness with Inspectors with EitherValues {

  "A QueryName" should {
    "be constructed properly" in {
      forAll((0 until 100)) { _ =>
        val q: QueryName = randomQueryName()
        q.value should not be empty
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
        val value = applyRef[QueryName](el)
        value.left.value
      }
    }
  }
}

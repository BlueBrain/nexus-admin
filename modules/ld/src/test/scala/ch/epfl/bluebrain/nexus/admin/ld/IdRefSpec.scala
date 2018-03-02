package ch.epfl.bluebrain.nexus.admin.ld

import ch.epfl.bluebrain.nexus.commons.test.Randomness
import org.scalatest.{Matchers, WordSpecLike}
import eu.timepit.refined.auto._
import cats.syntax.show._
import ch.epfl.bluebrain.nexus.admin.refined.ld.Id
class IdRefSpec extends WordSpecLike with Matchers with Randomness {
  "A IdRef" should {
    val idRef: IdRef = IdRef("nxv", "http:/example.com/prefix/", "rev")

    "construct a curie" in {
      idRef.curie shouldEqual Curie("nxv", "rev")
      idRef.show shouldEqual "nxv:rev"
    }

    "construct a prefix mapping" in {
      idRef.prefix shouldEqual Prefix("nxv", "http:/example.com/prefix/")
    }

    "construct an id" in {
      idRef.id shouldEqual ("http:/example.com/prefix/rev": Id)
    }
  }

}

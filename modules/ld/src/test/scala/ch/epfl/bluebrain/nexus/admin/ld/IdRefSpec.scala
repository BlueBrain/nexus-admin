package ch.epfl.bluebrain.nexus.admin.ld

import cats.syntax.show._
import ch.epfl.bluebrain.nexus.admin.refined.ld._
import ch.epfl.bluebrain.nexus.commons.test.Randomness
import eu.timepit.refined.auto._
import org.scalatest.{Matchers, WordSpecLike}

class IdRefSpec extends WordSpecLike with Matchers with Randomness {
  "A IdRef" should {
    val idRef: IdRef = IdRef("nxv", "http:/example.com/prefix/", "rev")

    "construct a curie" in {
      idRef.curie shouldEqual Curie("nxv", "rev")
      idRef.show shouldEqual "nxv:rev"
    }

    "construct a prefix mapping" in {
      idRef.prefixMapping shouldEqual PrefixMapping("nxv", "http:/example.com/prefix/")
    }

    "construct an id" in {
      idRef.id shouldEqual ("http:/example.com/prefix/rev": Id)
      idRef.id shouldEqual ("http:/example.com/prefix/rev": Id)

    }
  }

}

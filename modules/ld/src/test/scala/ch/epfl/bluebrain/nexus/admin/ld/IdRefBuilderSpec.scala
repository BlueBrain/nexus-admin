package ch.epfl.bluebrain.nexus.admin.ld

import ch.epfl.bluebrain.nexus.admin.refined.ld._
import eu.timepit.refined.auto._
import eu.timepit.refined.string._
import org.scalatest.{Matchers, WordSpecLike}

class IdRefBuilderSpec extends WordSpecLike with Matchers {
  "A IdRefBuilder" should {

    "construct a IdRef" in {
      IdRefBuilder("schema", "http://schema.org/").build("name") shouldEqual IdRef("schema", "http://schema.org/", "name")
    }

    "construct a IdRef from a Prefix" in {
      IdRefBuilder(Prefix("schema", "http://schema.org/")).build("name") shouldEqual IdRef("schema", "http://schema.org/", "name")
    }

    "construct a IdRef with random prefix name" in {
      val idRef = IdRefBuilder("http://schema.org/").build("name")
      idRef.reference shouldEqual ("name": PrefixName)
      idRef.prefixValue shouldEqual ("http://schema.org/": PrefixValue)
    }
  }

}

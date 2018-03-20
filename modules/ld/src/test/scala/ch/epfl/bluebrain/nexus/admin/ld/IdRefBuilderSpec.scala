package ch.epfl.bluebrain.nexus.admin.ld

import ch.epfl.bluebrain.nexus.admin.refined.ld._
import eu.timepit.refined.auto._
import eu.timepit.refined.string._
import org.scalatest.{Matchers, WordSpecLike}

class IdRefBuilderSpec extends WordSpecLike with Matchers {
  "A IdRefBuilder" should {

    "construct a IdRef" in {
      IdRefBuilder("schema", "http://schema.org/").build("name") shouldEqual IdRef("schema",
                                                                                   "http://schema.org/",
                                                                                   "name")
    }

    "construct a IdRef from a Prefix" in {
      IdRefBuilder(PrefixMapping("schema", "http://schema.org/")).build("name") shouldEqual IdRef("schema",
                                                                                                  "http://schema.org/",
                                                                                                  "name")
    }

    "generate a new builder when changing the prefix" in {
      val builder = IdRefBuilder(PrefixMapping("schema", "http://schema.org/"))
      builder.withPrefix("schema2").build("name") shouldEqual IdRef("schema2", "http://schema.org/", "name")
    }

    "construct a IdRef with random prefix" in {
      val idRef = IdRefBuilder("http://schema.org/").build("name")
      idRef.reference shouldEqual ("name": Prefix)
      idRef.namespace shouldEqual ("http://schema.org/": Namespace)
    }
  }

}

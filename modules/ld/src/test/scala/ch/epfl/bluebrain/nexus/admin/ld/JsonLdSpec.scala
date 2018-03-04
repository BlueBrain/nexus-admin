package ch.epfl.bluebrain.nexus.admin.ld

import akka.http.scaladsl.model.Uri
import ch.epfl.bluebrain.nexus.admin.ld.JsonLD.{IdTypeBlank, IdTypeUri}
import ch.epfl.bluebrain.nexus.admin.refined.ld.{PrefixValue, Reference}
import ch.epfl.bluebrain.nexus.commons.test.Resources
import org.scalatest.{Matchers, OptionValues, WordSpecLike}
import eu.timepit.refined.auto._
import io.circe.Json

class JsonLDSpec extends WordSpecLike with Matchers with Resources with OptionValues {

  "A JsonLD" should {
    val jsonLD            = JsonLD(jsonContentOf("/no_id.json"))
    val typedJsonLD       = JsonLD(jsonContentOf("/id_and_type.json"))
    val aliasedTypeJsonLD = JsonLD(jsonContentOf("/id_and_type_alias.json"))
    val schemaOrg         = new IdRefBuilder("schema", "http://schema.org/")

    "find a string from a given a predicate" in {
      jsonLD.predicate[String](schemaOrg.build("name")).value shouldEqual "The Empire State Building"
    }

    "find a uri from a given a predicate" in {
      jsonLD.predicate[Uri](schemaOrg.build("image")).value shouldEqual Uri(
        "http://www.civil.usherbrooke.ca/cours/gci215a/empire-state-building.jpg")
    }

    "find a float from a given a predicate" in {
      jsonLD.predicate[Float](schemaOrg.build("latitude")).value shouldEqual 40.75f
    }

    "find the @id" in {
      jsonLD.id.value shouldBe a[IdTypeBlank]
      typedJsonLD.id.value shouldEqual IdTypeUri("http://example.org/cars/for-sale#tesla")
      aliasedTypeJsonLD.id.value shouldEqual IdTypeUri(
        "https://bbp-nexus.epfl.ch/dev/v0/contexts/nexus/core/distribution/v0.1.0")
    }

    "find the @type" in {
      jsonLD.tpe shouldEqual None
      val idRef = typedJsonLD.tpe.value
      idRef.reference shouldEqual ("Offering": Reference)
      idRef.prefixValue shouldEqual ("http://some-example.org/something/": PrefixValue)
      aliasedTypeJsonLD.tpe.value shouldEqual IdRef("nxv",
                                                    "https://bbp-nexus.epfl.ch/vocabs/nexus/core/terms/v0.1.0/",
                                                    "Some")
    }

    "convert to Json" in {
      val json: Json = jsonLD
      json shouldEqual jsonLD.json
    }

    "deep merge" in {
      val mergedJson   = jsonLD.json deepMerge typedJsonLD.json
      val mergedJsonLD = jsonLD deepMerge typedJsonLD
      mergedJsonLD.json shouldEqual mergedJson
    }
  }
}

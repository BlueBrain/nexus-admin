package ch.epfl.bluebrain.nexus.admin.ld

import akka.http.scaladsl.model.Uri
import ch.epfl.bluebrain.nexus.admin.refined.ld.Uri._
import ch.epfl.bluebrain.nexus.admin.refined.ld._
import ch.epfl.bluebrain.nexus.commons.test.Resources
import eu.timepit.refined.auto._
import io.circe.Json
import org.scalatest.{Inspectors, Matchers, OptionValues, WordSpecLike}

class JsonLDSpec extends WordSpecLike with Matchers with Resources with OptionValues with Inspectors {

  "A JsonLD" should {
    val jsonLD            = JsonLD(jsonContentOf("/no_id.json"))
    val typedJsonLD       = JsonLD(jsonContentOf("/id_and_type.json"))
    val aliasedTypeJsonLD = JsonLD(jsonContentOf("/id_and_type_alias.json"))
    val schemaOrg         = new IdRefBuilder("schema", "http://schema.org/")

    "find a string from a given a predicate" in {
      jsonLD.value[String](schemaOrg.build("name")).value shouldEqual "The Empire State Building"
    }

    "find a uri from a given a predicate" in {
      jsonLD.value[Uri](schemaOrg.build("image")).value shouldEqual Uri(
        "http://www.civil.usherbrooke.ca/cours/gci215a/empire-state-building.jpg")
    }

    "find a uri from a given a predicate using refined" in {
      jsonLD.valueR[Id](schemaOrg.build("image")).value shouldEqual ("http://www.civil.usherbrooke.ca/cours/gci215a/empire-state-building.jpg" : Id)
    }

    "find a float from a given a predicate on a nested field" in {
      jsonLD.downFirst(schemaOrg.build("geo")).value[Float](schemaOrg.build("latitude")).value shouldEqual 40.75f
    }

    "find a list of elements from a given a predicate on a nested field" in {
      jsonLD.down(schemaOrg.build("geo")).foldLeft(List.empty[(Float, Float)]) {
        case (acc, c) =>
          val tuple = for {
            lat  <- c.value[Float](schemaOrg.build("latitude"))
            long <- c.value[Float](schemaOrg.build("longitude"))
          } yield (lat -> long)
          tuple.map(_ :: acc).getOrElse(acc)
      } should contain theSameElementsAs List(40.75f -> 73.98f, 10.0f -> 12.0f)
    }

    "failed to get a predicate from an unexisting parent predicate" in {
      jsonLD.downFirst(schemaOrg.build("nonExisting")).value[Float](schemaOrg.build("latitude")) shouldEqual None
    }

    "find the @id" in {
      jsonLD.id shouldBe a[IdTypeBlank]
      typedJsonLD.id shouldEqual IdTypeUri("http://example.org/cars/for-sale#tesla")
      aliasedTypeJsonLD.id shouldEqual IdTypeUri(
        "https://bbp-nexus.epfl.ch/dev/v0/contexts/nexus/core/distribution/v0.1.0")
    }

    "find the @type" in {
      jsonLD.tpe shouldEqual None
      val idRef = typedJsonLD.tpe.value
      idRef.reference shouldEqual ("Offering": Reference)
      idRef.namespace shouldEqual ("http://some-example.org/something/": Namespace)
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

    "return the namespace given a prefix" in {
      jsonLD.namespaceOf("xsd").value shouldEqual ("http://www.w3.org/2001/XMLSchema#": Namespace)
    }

    "return None attempting to fetch a namespace when the namespace in the json @context is wrong" in {
      jsonLD.namespaceOf("invalidNamespace") shouldEqual None
    }

    "return None attempting to fetch a namespace when the prefix does not exist in the json @context" in {
      jsonLD.namespaceOf("nonExisting") shouldEqual None
    }

    "return the prefix given a namespace" in {
      jsonLD.prefixOf("http://www.w3.org/2001/XMLSchema#").value shouldEqual ("xsd": Prefix)
    }

    "return None attempting to fetch a prefix when the namespace does not exist in the json @context" in {
      jsonLD.prefixOf("http://non-existinf.com/something/") shouldEqual None
    }

    "expand a value" in {
      jsonLD.expand("xsd:name").value shouldEqual ("http://www.w3.org/2001/XMLSchema#name": Id)
    }

    "expand a value already expanded returns the original value" in {
      jsonLD
        .expand("http://www.w3.org/2001/XMLSchema#name")
        .value shouldEqual ("http://www.w3.org/2001/XMLSchema#name": Id)
    }

    "fail to expand a value" in {
      forAll(List("", "xsd2:name", "something")) { value =>
        jsonLD.expand(value) shouldEqual None
      }
    }

    val noIdAddedContext = jsonContentOf("/no_id_added_context.json")

    "append a new context" in {
      val ctxToAdd = jsonContentOf("/added_context.json")
      jsonLD.appendContext(ctxToAdd).json shouldEqual noIdAddedContext
      jsonLD.appendContext(JsonLD(ctxToAdd)).json shouldEqual noIdAddedContext
    }
    "append nothing when the passed value does not contain @context" in {
      val ctxToAdd = Json.obj("one" -> Json.fromString("two"))
      jsonLD.appendContext(ctxToAdd).json shouldEqual jsonLD.json
    }
  }
}

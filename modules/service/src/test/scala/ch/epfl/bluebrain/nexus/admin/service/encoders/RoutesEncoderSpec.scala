package ch.epfl.bluebrain.nexus.admin.service.encoders

import ch.epfl.bluebrain.nexus.admin.refined.ld.Id
import ch.epfl.bluebrain.nexus.commons.http.ContextUri
import ch.epfl.bluebrain.nexus.commons.test.Randomness
import ch.epfl.bluebrain.nexus.commons.types.search.QueryResult.{ScoredQueryResult, UnscoredQueryResult}
import org.scalatest.{Matchers, WordSpecLike}
import eu.timepit.refined.api.RefType.applyRef
import io.circe.{Encoder, Json, Printer}
import io.circe.syntax._

class RoutesEncoderSpec extends WordSpecLike with Matchers with Randomness {

  case class TestEntity(id: Id, name: String)

  implicit val coreContext = ContextUri("http://localhost/core-context")

  implicit val extractId: (TestEntity) => Id = e => e.id
  val routesEncoder                          = new RoutesEncoder[TestEntity]()
  val id                                     = applyRef[Id](s"http://instance.com/${genString()}").toOption.get

  val testEntity = TestEntity(id, genString())
  implicit val entityEncoder: Encoder[TestEntity] = Encoder.encodeJson.contramap { e =>
    Json.obj(
      "@context" -> Json.fromString("http://localhost/entity-context"),
      "@id"      -> Json.fromString(e.id.value),
      "name"     -> Json.fromString(e.name)
    )
  }

  import routesEncoder._
  "RoutesEncoder" should {
    "encode unscored query results for ids" in {
      val result = UnscoredQueryResult(id)
      result.asJson.pretty(Printer.spaces2) shouldEqual
        s"""{
          |  "_id" : "${id.value}",
          |  "_source" : {
          |    "@id" : "${id.value}"
          |  }
          |}""".stripMargin

    }
    "encode scored query results for ids" in {
      val result = ScoredQueryResult(1.0f, id)
      result.asJson.pretty(Printer.spaces2) shouldEqual
        s"""{
           |  "_id" : "${id.value}",
           |  "_score" : 1.0,
           |  "_source" : {
           |    "@id" : "${id.value}"
           |  }
           |}""".stripMargin

    }
    "encode unscored query results for entities" in {
      val result = UnscoredQueryResult(testEntity)
      result.asJson.pretty(Printer.spaces2) shouldEqual
        s"""{
           |  "_id" : "${id.value}",
           |  "_source" : {
           |    "@context" : [
           |      "http://localhost/entity-context",
           |      "$coreContext"
           |    ],
           |    "@id" : "${id.value}",
           |    "name" : "${testEntity.name}"
           |  }
           |}""".stripMargin

    }
    "encode scored query results for entities" in {
      val result = ScoredQueryResult(1.0f, testEntity)
      result.asJson.pretty(Printer.spaces2) shouldEqual
        s"""{
           |  "_id" : "${id.value}",
           |  "_score" : 1.0,
           |  "_source" : {
           |    "@context" : [
           |      "http://localhost/entity-context",
           |      "$coreContext"
           |    ],
           |    "@id" : "${id.value}",
           |    "name" : "${testEntity.name}"
           |  }
           |}""".stripMargin
    }

  }

}

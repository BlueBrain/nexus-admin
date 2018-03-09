package ch.epfl.bluebrain.nexus.admin.core.persistence

import java.nio.charset.Charset
import java.time.Clock

import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.serialization.{SerializationExtension, SerializerWithStringManifest}
import ch.epfl.bluebrain.nexus.admin.core.persistence.Serializer.EventSerializer
import ch.epfl.bluebrain.nexus.admin.core.persistence.SerializerSpec.DataAndJson
import ch.epfl.bluebrain.nexus.admin.core.projects.Project.{Config, Value}
import ch.epfl.bluebrain.nexus.admin.core.resources.ResourceEvent
import ch.epfl.bluebrain.nexus.admin.core.resources.ResourceEvent._
import ch.epfl.bluebrain.nexus.admin.refined.ld.DecomposableId
import ch.epfl.bluebrain.nexus.commons.iam.acls.Meta
import ch.epfl.bluebrain.nexus.commons.iam.identity.Identity.UserRef
import eu.timepit.refined.auto._
import io.circe.Json
import io.circe.syntax._
import org.scalatest.{Inspectors, Matchers, WordSpecLike}
import shapeless.Typeable

class SerializerSpec extends WordSpecLike with Matchers with Inspectors with ScalatestRouteTest {

  private final val UTF8: Charset = Charset.forName("UTF-8")
  private final val serialization = SerializationExtension(system)

  def findConcreteSerializer[A <: SerializerWithStringManifest](o: AnyRef)(implicit t: Typeable[A]): A =
    t.cast(serialization.findSerializerFor(o)).getOrElse(fail("Expected a SerializerWithManifest"))

  "A Serializer" when {

    val projectId: DecomposableId = "https://bbp.epfl.ch/nexus/projects/projectid"
    val meta                      = Meta(UserRef("realm", "sub:1234"), Clock.systemUTC.instant())
    val tags                      = Set("project")

    "using EventSerializer" should {
      val results = List(
        DataAndJson(
          ResourceCreated(projectId,
                          1L,
                          meta,
                          tags,
                          Value(Json.obj("key" -> Json.fromString("http://localhost.com/path/")), Config(10L)).asJson),
          s"""{"id":"https://bbp.epfl.ch/nexus/projects/projectid","rev":1,"meta":{"author":{"id":"realms/realm/users/sub:1234","type":"UserRef"},"instant":"${meta.instant}"},"tags":["project"],"value":{"@context":{"key":"http://localhost.com/path/"},"config":{"maxAttachmentSize":10}},"type":"ResourceCreated"}"""
        ),
        DataAndJson(
          ResourceUpdated(
            projectId,
            1L,
            meta,
            tags,
            Value(Json.obj("key2" -> Json.fromString("http://localhost.com/path2/")), Config(20L)).asJson),
          s"""{"id":"https://bbp.epfl.ch/nexus/projects/projectid","rev":1,"meta":{"author":{"id":"realms/realm/users/sub:1234","type":"UserRef"},"instant":"${meta.instant}"},"tags":["project"],"value":{"@context":{"key2":"http://localhost.com/path2/"},"config":{"maxAttachmentSize":20}},"type":"ResourceUpdated"}"""
        ),
        DataAndJson(
          ResourceDeprecated(projectId, 1L, meta, tags),
          s"""{"id":"https://bbp.epfl.ch/nexus/projects/projectid","rev":1,"meta":{"author":{"id":"realms/realm/users/sub:1234","type":"UserRef"},"instant":"${meta.instant}"},"tags":["project"],"type":"ResourceDeprecated"}"""
        )
      )

      "encode known events to UTF-8" in {
        forAll(results) {
          case DataAndJson(event, json, _) =>
            val serializer = findConcreteSerializer[EventSerializer](event)
            new String(serializer.toBinary(event), UTF8) shouldEqual json
        }
      }

      "decode known events" in {
        forAll(results) {
          case DataAndJson(event, json, manifest) =>
            val serializer = findConcreteSerializer[EventSerializer](event)
            serializer.fromBinary(json.getBytes(UTF8), manifest) shouldEqual event
        }
      }
    }
  }
}

object SerializerSpec {

  /**
    * Holds both the JSON representation and the data structure
    *
    * @param event    instance of the data as a data structure
    * @param json     the JSON representation of the data
    * @param manifest the manifest to be used for selecting the appropriate resulting type
    */
  case class DataAndJson(event: ResourceEvent, json: String, manifest: String)

  object DataAndJson {
    def apply(event: ResourceEvent, json: String)(implicit tb: Typeable[ResourceEvent]): DataAndJson =
      DataAndJson(event, json, tb.describe)
  }

}

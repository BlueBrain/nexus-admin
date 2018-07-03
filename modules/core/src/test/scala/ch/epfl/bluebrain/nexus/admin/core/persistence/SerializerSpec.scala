package ch.epfl.bluebrain.nexus.admin.core.persistence

import java.nio.charset.Charset
import java.time.Clock
import java.util.UUID

import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.serialization.{SerializationExtension, SerializerWithStringManifest}
import ch.epfl.bluebrain.nexus.admin.core.TestHelper
import ch.epfl.bluebrain.nexus.admin.core.persistence.Serializer.EventSerializer
import ch.epfl.bluebrain.nexus.admin.core.persistence.SerializerSpec.DataAndJson
import ch.epfl.bluebrain.nexus.admin.core.resources.ResourceEvent
import ch.epfl.bluebrain.nexus.admin.core.resources.ResourceEvent._
import ch.epfl.bluebrain.nexus.admin.refined.ld.Id
import ch.epfl.bluebrain.nexus.commons.types.Meta
import ch.epfl.bluebrain.nexus.commons.types.identity.Identity.UserRef
import eu.timepit.refined.api.RefType.refinedRefType
import eu.timepit.refined.auto._
import org.scalatest.{Inspectors, Matchers, WordSpecLike}
import shapeless.Typeable

class SerializerSpec extends WordSpecLike with Matchers with Inspectors with ScalatestRouteTest with TestHelper {

  private final val UTF8: Charset = Charset.forName("UTF-8")
  private final val serialization = SerializationExtension(system)

  def findConcreteSerializer[A <: SerializerWithStringManifest](o: AnyRef)(implicit t: Typeable[A]): A =
    t.cast(serialization.findSerializerFor(o)).getOrElse(fail("Expected a SerializerWithManifest"))

  "A Serializer" when {

    val projectId: Id = "https://bbp.epfl.ch/nexus/projects/projectid"
    val meta          = Meta(UserRef("realm", "sub:1234"), Clock.systemUTC.instant())
    val tags          = Set("project")

    "using EventSerializer" should {
      val label  = "projectid"
      val value  = genProjectValue()
      val value2 = genProjectValue()
      val uuid   = UUID.randomUUID().toString
      val uuid2  = UUID.randomUUID().toString
      val results = List(
        DataAndJson(
          ResourceCreated(projectId, label, uuid, Some(uuid2), 1L, meta, tags, value),
          s"""{"id":"https://bbp.epfl.ch/nexus/projects/projectid","label":"$label","uuid":"$uuid","parentUuid":"$uuid2","rev":1,"meta":{"author":{"id":"realms/realm/users/sub:1234","type":"UserRef"},"instant":"${meta.instant}"},"tags":["project"],"value":${value.noSpaces},"type":"ResourceCreated"}"""
        ),
        DataAndJson(
          ResourceUpdated(projectId, uuid, None, 1L, meta, tags, value2),
          s"""{"id":"https://bbp.epfl.ch/nexus/projects/projectid","uuid":"$uuid","parentUuid":null,"rev":1,"meta":{"author":{"id":"realms/realm/users/sub:1234","type":"UserRef"},"instant":"${meta.instant}"},"tags":["project"],"value":${value2.noSpaces},"type":"ResourceUpdated"}"""
        ),
        DataAndJson(
          ResourceDeprecated(projectId, uuid, None, 1L, meta, tags),
          s"""{"id":"https://bbp.epfl.ch/nexus/projects/projectid","uuid":"$uuid","parentUuid":null,"rev":1,"meta":{"author":{"id":"realms/realm/users/sub:1234","type":"UserRef"},"instant":"${meta.instant}"},"tags":["project"],"type":"ResourceDeprecated"}"""
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

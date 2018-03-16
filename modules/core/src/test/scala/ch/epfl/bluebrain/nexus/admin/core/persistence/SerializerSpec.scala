package ch.epfl.bluebrain.nexus.admin.core.persistence

import java.nio.charset.Charset
import java.time.Clock

import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.serialization.{SerializationExtension, SerializerWithStringManifest}
import ch.epfl.bluebrain.nexus.admin.core.persistence.Serializer.EventSerializer
import ch.epfl.bluebrain.nexus.admin.core.persistence.SerializerSpec.DataAndJson
import ch.epfl.bluebrain.nexus.admin.core.projects.Project.{Config, LoosePrefixMapping, ProjectValue}
import ch.epfl.bluebrain.nexus.admin.core.resources.ResourceEvent
import ch.epfl.bluebrain.nexus.admin.core.resources.ResourceEvent._
import ch.epfl.bluebrain.nexus.admin.ld.Const._
import ch.epfl.bluebrain.nexus.admin.refined.ld.Id
import ch.epfl.bluebrain.nexus.commons.iam.acls.Meta
import ch.epfl.bluebrain.nexus.commons.iam.identity.Identity.UserRef
import eu.timepit.refined.api.RefType.refinedRefType
import eu.timepit.refined.auto._
import io.circe.syntax._
import org.scalatest.{Inspectors, Matchers, WordSpecLike}
import shapeless.Typeable

class SerializerSpec extends WordSpecLike with Matchers with Inspectors with ScalatestRouteTest {

  private final val UTF8: Charset = Charset.forName("UTF-8")
  private final val serialization = SerializationExtension(system)

  def findConcreteSerializer[A <: SerializerWithStringManifest](o: AnyRef)(implicit t: Typeable[A]): A =
    t.cast(serialization.findSerializerFor(o)).getOrElse(fail("Expected a SerializerWithManifest"))

  "A Serializer" when {

    val projectId: Id = "https://bbp.epfl.ch/nexus/projects/projectid"
    val meta          = Meta(UserRef("realm", "sub:1234"), Clock.systemUTC.instant())
    val tags          = Set("project")

    val prefixMappings = List(
      LoosePrefixMapping(nxv.prefixBuilder, refinedRefType.unsafeRewrap(nxv.namespaceBuilder)),
      LoosePrefixMapping(rdf.prefixBuilder, refinedRefType.unsafeRewrap(rdf.namespaceBuilder))
    )

    "using EventSerializer" should {
      val results = List(
        DataAndJson(
          ResourceCreated(projectId, 1L, meta, tags, ProjectValue(Some("label"), Some("desc"), prefixMappings, Config(10L)).asJson),
          s"""{"id":"https://bbp.epfl.ch/nexus/projects/projectid","rev":1,"meta":{"author":{"id":"realms/realm/users/sub:1234","type":"UserRef"},"instant":"${meta.instant}"},"tags":["project"],"value":{"label":"label","description":"desc","prefixMappings":[{"prefix":"nxv","namespace":"https://bbp-nexus.epfl.ch/vocabs/nexus/core/terms/v0.1.0/"},{"prefix":"rdf","namespace":"http://www.w3.org/1999/02/22-rdf-syntax-ns#"}],"config":{"maxAttachmentSize":10}},"type":"ResourceCreated"}"""
        ),
        DataAndJson(
          ResourceUpdated(projectId, 1L, meta, tags, ProjectValue(Some("label2"), Some("desc2"), prefixMappings, Config(20L)).asJson),
          s"""{"id":"https://bbp.epfl.ch/nexus/projects/projectid","rev":1,"meta":{"author":{"id":"realms/realm/users/sub:1234","type":"UserRef"},"instant":"${meta.instant}"},"tags":["project"],"value":{"label":"label2","description":"desc2","prefixMappings":[{"prefix":"nxv","namespace":"https://bbp-nexus.epfl.ch/vocabs/nexus/core/terms/v0.1.0/"},{"prefix":"rdf","namespace":"http://www.w3.org/1999/02/22-rdf-syntax-ns#"}],"config":{"maxAttachmentSize":20}},"type":"ResourceUpdated"}"""
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

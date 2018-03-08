package ch.epfl.bluebrain.nexus.admin.core.persistence

import java.time.Clock

import akka.persistence.journal.Tagged
import ch.epfl.bluebrain.nexus.admin.core.resources.ResourceEvent._
import ch.epfl.bluebrain.nexus.admin.refined.ld.DecomposableId
import ch.epfl.bluebrain.nexus.commons.iam.acls.Meta
import ch.epfl.bluebrain.nexus.commons.iam.identity.Identity.UserRef
import ch.epfl.bluebrain.nexus.commons.test.Randomness
import eu.timepit.refined.auto._
import io.circe.Json
import org.scalatest.{Inspectors, Matchers, WordSpecLike}

class TaggingAdapterSpec extends WordSpecLike with Matchers with Inspectors with Randomness {

  def genJson(): Json = Json.obj("key" -> Json.fromString(genString()))

  private case class OtherEvent(some: String)

  "A TaggingAdapter" should {

    val adapter = new TaggingAdapter()

    val id: DecomposableId = "https://bbp.epfl.ch/nexus/projects/projectid"
    val meta               = Meta(UserRef("realm", "sub:1234"), Clock.systemUTC.instant())

    val mapping = Map(
      ResourceCreated(id, 1L, meta, Set("project", "other"), genJson()) -> Set("project", "other"),
      ResourceUpdated(id, 2L, meta, Set("project", "one"), genJson())   -> Set("project", "one"),
      ResourceDeprecated(id, 3L, meta, Set("project"))                  -> Set("project")
    )

    "set the appropriate tags" in {
      forAll(mapping.toList) {
        case (ev, tags) => adapter.toJournal(ev) shouldEqual Tagged(ev, tags)
      }
    }

    "return an empty manifest" in {
      adapter.manifest(OtherEvent(genString())) shouldEqual ""
    }

  }
}

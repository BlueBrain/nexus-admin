package ch.epfl.bluebrain.nexus.admin.core.projects

import java.util.regex.Pattern.quote

import ch.epfl.bluebrain.nexus.admin.core.TestHepler
import ch.epfl.bluebrain.nexus.admin.core.config.AppConfig.ProjectsConfig
import ch.epfl.bluebrain.nexus.admin.core.projects.Project.ProjectValue
import ch.epfl.bluebrain.nexus.commons.test.Resources
import eu.timepit.refined.api.RefType.refinedRefType
import eu.timepit.refined.auto._
import io.circe.generic.auto._
import io.circe.parser._
import io.circe.syntax._
import org.scalatest.{Inspectors, Matchers, WordSpecLike}

import scala.concurrent.duration._

class ProjectValueSpec extends WordSpecLike with Matchers with Inspectors with Resources with TestHepler {

  private val projValue: ProjectValue = genProjectValue()

  "A ProjectValue" should {
    implicit val config: ProjectsConfig = ProjectsConfig(3 seconds, "https://nexus.example.ch/v1/projects/", 100000L)

    val replacements = Map(
      quote("{{name}}")        -> projValue.name,
      quote("{{description}}") -> projValue.description.get,
      quote(""""{{size}}"""")  -> projValue.config.maxAttachmentSize.toString
    )
    val json      = jsonContentOf("/project/project-value.json", replacements)
    val jsonNoAtt = jsonContentOf("/project/project-value-no-att.json", replacements)

    val list =
      List(projValue                                                                                 -> json,
           projValue.copy(config = projValue.config.copy(maxAttachmentSize = config.attachmentSize)) -> jsonNoAtt)

    "be encoded properly into json" in {
      projValue.asJson shouldEqual json
    }

    "be decoded properly from json" in {
      forAll(list) {
        case (model, j) => decode[ProjectValue](j.noSpaces) shouldEqual Right(model)
      }
    }
  }
}

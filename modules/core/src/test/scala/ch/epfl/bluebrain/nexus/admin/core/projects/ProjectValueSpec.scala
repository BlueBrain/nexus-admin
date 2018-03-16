package ch.epfl.bluebrain.nexus.admin.core.projects

import ch.epfl.bluebrain.nexus.admin.core.config.AppConfig.ProjectsConfig
import ch.epfl.bluebrain.nexus.admin.core.projects.Project.{Config, LoosePrefixMapping, ProjectValue}
import ch.epfl.bluebrain.nexus.admin.ld.Const.{nxv, rdf}
import ch.epfl.bluebrain.nexus.commons.test.Resources
import eu.timepit.refined.api.RefType.refinedRefType
import eu.timepit.refined.auto._
import io.circe.generic.auto._
import io.circe.parser._
import io.circe.syntax._
import org.scalatest.{Inspectors, Matchers, WordSpecLike}

import scala.concurrent.duration._

class ProjectValueSpec extends WordSpecLike with Matchers with Inspectors with Resources {

  private val projectValue: ProjectValue = {
    val prefixMappings = List(
      LoosePrefixMapping(nxv.prefixBuilder, refinedRefType.unsafeRewrap(nxv.namespaceBuilder)),
      LoosePrefixMapping(rdf.tpe.prefix, refinedRefType.unsafeRewrap(rdf.tpe.id))
    )
    ProjectValue(Some("label"), Some("description"), prefixMappings, Config(200L))
  }
  "A ProjectValue" should {
    implicit val config: ProjectsConfig = ProjectsConfig(3 seconds, "https://nexus.example.ch/v1/projects/", 100000L)

    val json      = jsonContentOf("/project/project-value.json")
    val jsonNoAtt = jsonContentOf("/project/project-value-no-att.json")

    val list =
      List(projectValue                                                                                    -> json,
           projectValue.copy(config = projectValue.config.copy(maxAttachmentSize = config.attachmentSize)) -> jsonNoAtt)
    "be encoded properly into json" in {
      projectValue.asJson shouldEqual json
    }

    "be decoded properly from json" in {
      forAll(list) {
        case (model, j) => decode[ProjectValue](j.noSpaces) shouldEqual Right(model)
      }
    }
  }
}

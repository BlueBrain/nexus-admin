package ch.epfl.bluebrain.nexus.admin.refined

import akka.http.scaladsl.model.Uri
import ch.epfl.bluebrain.nexus.admin.refined.ld.Id
import ch.epfl.bluebrain.nexus.commons.test.Randomness
import org.scalatest.{Matchers, WordSpecLike}
import eu.timepit.refined.api.RefType.applyRef
import ch.epfl.bluebrain.nexus.admin.refined.project._

class IdToProjectReferenceOpsSpec extends WordSpecLike with Matchers with Randomness {

  "IdToProjectReferenceOpsSpec" should {

    val baseUri: Uri = s"http://example.com/${genString()}"
    "convert Id to Project reference when Id starts with projects base" in {
      val projId  = genString(10)
      val validId = applyRef[Id](s"$baseUri/projects/$projId").toOption.get
      validId.toProjectReference(baseUri).get.value shouldEqual projId

    }

    "not convert Id to Project reference when Id doesn't start with projects base" in {
      val projId    = genString()
      val invalidId = applyRef[Id](s"$baseUri/notprojects/$projId").toOption.get
      invalidId.toProjectReference(baseUri) shouldEqual None
    }
  }

}

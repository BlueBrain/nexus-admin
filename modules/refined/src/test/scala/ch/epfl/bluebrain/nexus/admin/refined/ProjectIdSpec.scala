package ch.epfl.bluebrain.nexus.admin.refined

import ch.epfl.bluebrain.nexus.admin.refined.ld.{Id, Namespace}
import ch.epfl.bluebrain.nexus.admin.refined.project.ProjectId
import ch.epfl.bluebrain.nexus.commons.test.Randomness
import eu.timepit.refined.api.RefType.applyRef
import org.scalatest.{EitherValues, Inspectors, Matchers, WordSpecLike}
import eu.timepit.refined.auto._

class ProjectIdSpec extends WordSpecLike with Matchers with Randomness with Inspectors with EitherValues {
  "A ProjectId" should {

    implicit val projectNamespace: Namespace = "https://nexus.example.com/projects/"
    "be constructed properly" in {

      val list = Vector.fill(100) {
        applyRef[Id](s"https://nexus.example.com/projects/${genString(5)}").toOption.get
      }
      forAll(list) { el =>
        val value = applyRef[ProjectId](el)
        value.right.value.value shouldEqual el
      }

    }

    "fail to contstruct when namespace doesn't match" in {
      val invalidId = applyRef[Id](s"https://nexus.example.com/notprojects/${genString(5)}").toOption.get
      applyRef[ProjectId](invalidId).left.value shouldEqual s"ValidConvertibleProjectUri predicate failed: https://nexus.example.com/notprojects/ in ${invalidId.value} is not a valid project namespace, expected ${projectNamespace.value}"

    }

    "fail to contstruct when project reference is not valid" in {
      val invalidProjectReference = genString(17)
      val invalidId               = applyRef[Id](s"https://nexus.example.com/projects/$invalidProjectReference").toOption.get
      applyRef[ProjectId](invalidId).left.value shouldEqual s"ValidConvertibleProjectUri predicate failed: $invalidProjectReference in ${invalidId.value} is not a valid ProjectReference"

    }
  }
}

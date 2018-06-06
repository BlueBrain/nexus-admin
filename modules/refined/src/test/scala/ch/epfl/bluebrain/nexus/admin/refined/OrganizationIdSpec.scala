package ch.epfl.bluebrain.nexus.admin.refined

import ch.epfl.bluebrain.nexus.admin.refined.organization.OrganizationId
import ch.epfl.bluebrain.nexus.admin.refined.ld.{Id, Namespace}
import ch.epfl.bluebrain.nexus.commons.test.Randomness
import eu.timepit.refined.api.RefType.applyRef
import eu.timepit.refined.auto._
import org.scalatest.{EitherValues, Inspectors, Matchers, WordSpecLike}

class OrganizationIdSpec extends WordSpecLike with Matchers with Randomness with Inspectors with EitherValues {
  "A OrganizationId" should {

    implicit val organizationNamespace: Namespace = "https://nexus.example.com/orgs/"
    "be constructed properly" in {

      val list = Vector.fill(100) {
        applyRef[Id](s"https://nexus.example.com/orgs/${genString(5)}").toOption.get
      }
      forAll(list) { el =>
        val value = applyRef[OrganizationId](el)
        value.right.value.value shouldEqual el
      }

    }

    "fail to construct when namespace doesn't match" in {
      val invalidId = applyRef[Id](s"https://nexus.example.com/notorgs/${genString(5)}").toOption.get
      applyRef[OrganizationId](invalidId).left.value shouldEqual s"ValidConvertibleOrganizationUri predicate failed: https://nexus.example.com/notorgs/ in ${invalidId.value} is not a valid organization namespace, expected ${organizationNamespace.value}"

    }

    "fail to construct when organization reference is not valid" in {
      val invalidOrganizationReference = genString(17)
      val invalidId                    = applyRef[Id](s"https://nexus.example.com/orgs/$invalidOrganizationReference").toOption.get
      applyRef[OrganizationId](invalidId).left.value shouldEqual s"ValidConvertibleOrganizationUri predicate failed: $invalidOrganizationReference in ${invalidId.value} is not a valid OrganizationReference"

    }
  }
}

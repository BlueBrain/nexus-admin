package ch.epfl.bluebrain.nexus.admin.refined

import ch.epfl.bluebrain.nexus.admin.refined.permissions._
import ch.epfl.bluebrain.nexus.commons.types.identity.Identity
import ch.epfl.bluebrain.nexus.iam.client.types._
import ch.epfl.bluebrain.nexus.service.http.Path
import eu.timepit.refined.api.RefType.applyRef
import eu.timepit.refined.auto._
import org.scalatest.{EitherValues, Matchers, WordSpecLike}

class PermissionsContainsSpec extends WordSpecLike with Matchers with EitherValues {
  "A PermissionsContains type" should {
    val perms =
      FullAccessControlList(
        (Identity.Anonymous(), Path./, Permissions(Permission("projects/read"), Permission("projects/write")))
      )

    "be constructed" in {
      applyRef[HasReadProjects](perms).right.value
      applyRef[HasWriteProjects](perms).right.value
    }

    "fail to construct" in {
      applyRef[HasCreateProjects](perms).left.value
    }

    "be constructed from manage" in {
      val perms =
        FullAccessControlList(
          (Identity.Anonymous(), Path./, Permissions(Permission("projects/manage")))
        )
      applyRef[HasReadProjects](perms).right.value
      applyRef[HasWriteProjects](perms).right.value
      applyRef[HasCreateProjects](perms).right.value
    }

    "be constructed with inference" in {
      val perms =
        FullAccessControlList(
          (Identity.Anonymous(), Path./, Permissions(Permission("projects/manage")))
        )
      applyRef[HasManageProjects](perms).right.value: HasReadProjects
      applyRef[HasManageProjects](perms).right.value: HasCreateProjects
      applyRef[HasManageProjects](perms).right.value: HasWriteProjects
    }
  }
}

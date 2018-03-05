package ch.epfl.bluebrain.nexus.admin.core.types

import cats.Show
import ch.epfl.bluebrain.nexus.admin.refined.project.ProjectReference

object ProjectReferenceOps {
  // $COVERAGE-OFF$
  final implicit val projectReferenceShow: Show[ProjectReference] = Show.show(_.value)
  // $COVERAGE-ON$
}

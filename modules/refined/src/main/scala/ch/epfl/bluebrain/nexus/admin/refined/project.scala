package ch.epfl.bluebrain.nexus.admin.refined

import ch.epfl.bluebrain.nexus.admin.refined.ld.IRelativeRef
import ch.epfl.bluebrain.nexus.admin.refined.project.ProjectReferencePredicate
import eu.timepit.refined.W
import eu.timepit.refined.api.{Inference, Refined}
import eu.timepit.refined.string.MatchesRegex

object project extends ProjectInferences {
  type ProjectReferencePredicate = MatchesRegex[W.`"[a-zA-Z0-9-_]{3,16}"`.T]
  type ProjectReference          = String Refined ProjectReferencePredicate
}

trait ProjectInferences {
  // $COVERAGE-OFF$
  final implicit val inference: Inference[ProjectReferencePredicate, IRelativeRef] =
    Inference.alwaysValid("A Project Reference is always a valid IRelativeRef")
  // $COVERAGE-ON$

}

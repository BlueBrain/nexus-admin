package ch.epfl.bluebrain.nexus.admin.refined

import akka.http.scaladsl.model.Uri
import ch.epfl.bluebrain.nexus.admin.refined.ld.{IRelativeRef, Id}
import ch.epfl.bluebrain.nexus.admin.refined.project.ProjectReferencePredicate
import eu.timepit.refined.W
import eu.timepit.refined.api.Inference.==>
import eu.timepit.refined.api.RefType.applyRef
import eu.timepit.refined.api.{Inference, Refined}
import eu.timepit.refined.string.MatchesRegex

object project extends ProjectInferences {
  type ProjectReferencePredicate = MatchesRegex[W.`"[a-zA-Z0-9-_]{3,16}"`.T]
  type ProjectReference          = String Refined ProjectReferencePredicate

  implicit class IdToProjectReferenceOps(id: Id) {

    def toProjectReference(base: Uri): Option[ProjectReference] = {
      val projectsPrefix = s"$base/projects/"
      if (id.value.startsWith(projectsPrefix)) {
        applyRef[ProjectReference](id.value.stripPrefix(projectsPrefix)).toOption
      } else {
        None
      }

    }
  }

}

trait ProjectInferences {
  // $COVERAGE-OFF$
  final implicit val projectInference: ProjectReferencePredicate ==> IRelativeRef =
    Inference.alwaysValid("A Project Reference is always a valid IRelativeRef")
  // $COVERAGE-ON$

}

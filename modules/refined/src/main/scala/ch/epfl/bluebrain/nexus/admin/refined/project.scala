package ch.epfl.bluebrain.nexus.admin.refined

import cats.Show
import ch.epfl.bluebrain.nexus.admin.refined.ld.{IRelativeRef, Id, Namespace}
import ch.epfl.bluebrain.nexus.admin.refined.organization.OrganizationReference
import ch.epfl.bluebrain.nexus.admin.refined.project.ProjectReference
import eu.timepit.refined.W
import eu.timepit.refined.api.Inference.==>
import eu.timepit.refined.api.RefType.applyRef
import eu.timepit.refined.api.{Inference, Refined}
import eu.timepit.refined.string.MatchesRegex

object project extends ProjectInferences {

  type ProjectLabelPredicate = MatchesRegex[W.`"[a-zA-Z0-9-_]{3,32}"`.T]
  type ProjectLabel          = String Refined ProjectLabelPredicate

  case class ProjectReference(organizationReference: OrganizationReference, projectLabel: ProjectLabel)

  implicit val projectReferenceShow: Show[ProjectReference] = Show.show { pr =>
    s"${pr.organizationReference}/${pr.projectLabel}"
  }

  /**
    * Interface syntax to expose new functionality into [[Id]] type.
    *
    * @param value the instance of a [[Id]]
    */
  implicit class ProjectReferenceSyntax(value: Id) {

    private val projectReferencePattern = "([a-zA-Z0-9-_]{3,32})/([a-zA-Z0-9-_]{3,32})".r

    /**
      * Try to extract [[ProjectReference]] from [[Id]]
      * @param projectNamespace implicit [[Namespace]] that must match if the match is to succeed
      * @return [[Option]] of [[ProjectReference]]
      */
    def projectReference(implicit projectNamespace: Namespace): Option[ProjectReference] =
      value.decompose(projectNamespace) match {
        case (`projectNamespace`, ref) =>
          ref.value match {
            case projectReferencePattern(org, proj) =>
              Some(
                ProjectReference(applyRef[OrganizationReference].unsafeFrom(org),
                                 applyRef[ProjectLabel].unsafeFrom(proj)))
            case _ => None
          }
        case _ => None
      }
  }

}

trait ProjectInferences {
  // $COVERAGE-OFF$
  final implicit val projectInference: ProjectReference ==> IRelativeRef =
    Inference.alwaysValid("A Project Reference is always a valid IRelativeRef")
}

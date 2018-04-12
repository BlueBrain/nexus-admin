package ch.epfl.bluebrain.nexus.admin.refined

import ch.epfl.bluebrain.nexus.admin.refined.ld.{IRelativeRef, Id, Namespace}
import ch.epfl.bluebrain.nexus.admin.refined.project.{ProjectId, ProjectReferencePredicate}
import eu.timepit.refined.W
import eu.timepit.refined.api.Inference.==>
import eu.timepit.refined.api.RefType.{applyRef, refinedRefType}
import eu.timepit.refined.api.{Inference, Refined, Validate}
import eu.timepit.refined.string.MatchesRegex

import scala.util.Try

object project extends ProjectInferences {
  type ProjectReferencePredicate = MatchesRegex[W.`"[a-zA-Z0-9-_]{3,16}"`.T]
  type ProjectReference          = String Refined ProjectReferencePredicate

  type ProjectId = Id Refined ProjectUri

  final case class ProjectUri()

  object ProjectUri {
    private[project] def unsafeDecompose(id: Id)(
        implicit projectNamespace: Namespace): (Namespace, ProjectReference) = {
      id.decompose match {
        case (namespace, ref) if namespace.equals(projectNamespace) =>
          applyRef[ProjectReference](ref.value) match {
            case Right(projectReference) => (namespace, projectReference)
            case Left(_)                 => throw new IllegalArgumentException(s"$ref in ${id.value} is not a valid ProjectReference")
          }
        case (namespace, _) =>
          throw new IllegalArgumentException(
            s"$namespace in ${id.value} is not a valid project namespace, expected ${projectNamespace.value}")
      }

    }
    final implicit def uriValidate(implicit projectNamespace: Namespace): Validate.Plain[Id, ProjectUri] =
      Validate.fromPartial(unsafeDecompose, "ValidConvertibleProjectUri", ProjectUri())

    /**
      * Interface syntax to expose new functionality into [[Id]] type.
      *
      * @param value the instance of a [[Id]]
      */
    implicit class ProjectReferenceSyntax(value: Id) {

      /**
        * Try to extract [[ProjectReference]] from [[Id]]
        * @param projectNamespace implicit [[Namespace]] that must match if the match is to succeed
        * @return [[Option]] of [[ProjectReference]]
        */
      def projectReference(implicit projectNamespace: Namespace): Option[ProjectReference] =
        Try(unsafeDecompose(value)).toOption.map { case (_, ref) => ref }
    }

  }

}

trait ProjectInferences {
  // $COVERAGE-OFF$
  final implicit val projectInference: ProjectReferencePredicate ==> IRelativeRef =
    Inference.alwaysValid("A Project Reference is always a valid IRelativeRef")

  final implicit val projectIdInference: ProjectId ==> Id =
    Inference.alwaysValid("A ProjectId is always a valid Id")
  // $COVERAGE-ON$

}

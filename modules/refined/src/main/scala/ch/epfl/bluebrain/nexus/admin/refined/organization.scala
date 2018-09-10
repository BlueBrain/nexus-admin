package ch.epfl.bluebrain.nexus.admin.refined

import ch.epfl.bluebrain.nexus.admin.refined.ld.{IRelativeRef, Id, Namespace}
import ch.epfl.bluebrain.nexus.admin.refined.organization.{OrganizationId, OrganizationReferencePredicate}
import eu.timepit.refined.W
import eu.timepit.refined.api.Inference.==>
import eu.timepit.refined.api.RefType.applyRef
import eu.timepit.refined.api.{Inference, Refined, Validate}
import eu.timepit.refined.string.MatchesRegex

import scala.util.Try

object organization extends OrganizationInferences {

  type OrganizationReferencePredicate = MatchesRegex[W.`"[a-zA-Z0-9-_]{3,32}"`.T]
  type OrganizationReference          = String Refined OrganizationReferencePredicate

  type OrganizationId = Id Refined OrganizationUri

  final case class OrganizationUri()

  object OrganizationUri {

    private[organization] def unsafeDecompose(id: Id)(
        implicit organizationNamespace: Namespace): (Namespace, OrganizationReference) = {
      id.decompose match {
        case (`organizationNamespace`, ref) =>
          applyRef[OrganizationReference](ref.value) match {
            case Right(organizationReference) => (organizationNamespace, organizationReference)
            case Left(_) =>
              throw new IllegalArgumentException(s"$ref in ${id.value} is not a valid OrganizationReference")
          }
        case (namespace, _) =>
          throw new IllegalArgumentException(
            s"$namespace in ${id.value} is not a valid organization namespace, expected ${organizationNamespace.value}")
      }

    }
    final implicit def uriValidate(implicit organizationNamespace: Namespace): Validate.Plain[Id, OrganizationUri] =
      Validate.fromPartial(unsafeDecompose, "ValidConvertibleOrganizationUri", OrganizationUri())

    implicit class OrganizationReferenceSyntax(value: Id) {

      def organizationReference(implicit organizationNamespace: Namespace): Option[OrganizationReference] =
        Try(unsafeDecompose(value)).toOption.map { case (_, ref) => ref }
    }
  }

}

trait OrganizationInferences {

  final implicit val organizationInference: OrganizationReferencePredicate ==> IRelativeRef =
    Inference.alwaysValid("An Organization Reference is always a valid IRelativeRef")

  final implicit val organizationIdInference: OrganizationId ==> Id =
    Inference.alwaysValid("An OrganizationId is always a valid Id")

}

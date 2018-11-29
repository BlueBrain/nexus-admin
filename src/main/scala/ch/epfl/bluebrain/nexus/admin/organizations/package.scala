package ch.epfl.bluebrain.nexus.admin
import ch.epfl.bluebrain.nexus.admin.types.ResourceMetadata
import ch.epfl.bluebrain.nexus.sourcing.Aggregate

package object organizations {

  type Agg[F[_]] =
    Aggregate[F, String, OrganizationEvent, OrganizationState, OrganizationCommand, OrganizationRejection]

  type OrganizationMetaOrRejection = Either[OrganizationRejection, ResourceMetadata]
  type EventOrRejection            = Either[OrganizationRejection, OrganizationEvent]
}

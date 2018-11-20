package ch.epfl.bluebrain.nexus.admin.organizations

import java.util.UUID

import ch.epfl.bluebrain.nexus.admin.types.ResourceMetadata
import ch.epfl.bluebrain.nexus.commons.types.identity.Identity

class Organizations[F[_]] {

  type OrganizationMetaOrRejection = Either[OrganizationRejection, ResourceMetadata]

  def create(organization: Organization)(implicit caller: Identity): F[OrganizationMetaOrRejection]            = ???
  def update(organization: Organization, rev: Long)(implicit caller: Identity): F[OrganizationMetaOrRejection] = ???
  def deprecate(id: UUID, rev: Long)(implicit caller: Identity): F[OrganizationMetaOrRejection]              = ???

}

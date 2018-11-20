package ch.epfl.bluebrain.nexus.admin.organizations
import ch.epfl.bluebrain.nexus.commons.types.Rejection

sealed trait OrganizationRejection extends Rejection

case object OrganizationAlreadyExistsRejection extends OrganizationRejection

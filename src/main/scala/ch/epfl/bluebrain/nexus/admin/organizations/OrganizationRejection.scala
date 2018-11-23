package ch.epfl.bluebrain.nexus.admin.organizations
import java.util.UUID

import ch.epfl.bluebrain.nexus.commons.types.Rejection

sealed trait OrganizationRejection extends Rejection

/**
  * Signals the the organization already exists.
  */
case object OrganizationAlreadyExistsRejection extends OrganizationRejection

/**
  * Signals that the organization does not exist.
  */
case object OrganizationDoesNotExistRejection extends OrganizationRejection

/**
  * Signals that the provided revision does not match the latest revision
  * @param latestRev    latest know revision
  * @param providedRev  provided revision
  */
final case class IncorrectOrganizationRevRejection(latestRev: Long, providedRev: Long) extends OrganizationRejection

/**
  * Organization was in unexpected state
  * @param id ID of the organization.
  */
final case class OrganizationUnexpectedState(id: UUID) extends OrganizationRejection

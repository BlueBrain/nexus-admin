package ch.epfl.bluebrain.nexus.admin.organizations

import ch.epfl.bluebrain.nexus.commons.types.Rejection

sealed trait OrganizationRejection extends Rejection

/**
  * Signals the the organization already exists.
  */
case object OrganizationAlreadyExists extends OrganizationRejection

/**
  * Signals that the organization does not exist.
  */
case object OrganizationDoesNotExist extends OrganizationRejection

/**
  * Signals that the provided revision does not match the latest revision
  * @param latestRev    latest know revision
  * @param providedRev  provided revision
  */
final case class IncorrectRevisionProvided(latestRev: Long, providedRev: Long) extends OrganizationRejection

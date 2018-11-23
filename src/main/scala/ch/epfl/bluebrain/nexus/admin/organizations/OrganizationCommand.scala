package ch.epfl.bluebrain.nexus.admin.organizations
import java.time.Instant
import java.util.UUID

import ch.epfl.bluebrain.nexus.commons.types.identity.Identity

/**
  * Enumeration of Organization collection command types.
  */
sealed trait OrganizationCommand extends Product with Serializable {

  /**
    * @return ID of the organization
    */
  def id: UUID

  /**
    * @return the revision that this command generated
    */
  def rev: Long

  /**
    * @return the instant when this command was created
    */
  def instant: Instant

  /**
    * @return the subject which created this command
    */
  def subject: Identity
}

object OrganizationCommand {

  /**
    * An intent to create an organization.
    * @param id           ID of the organization
    * @param organization representation of the organization
    * @param rev          the revision to create
    * @param instant      the instant when this command was created
    * @param subject      the subject which created this command.
    */
  final case class CreateOrganization(id: UUID,
                                      organization: Organization,
                                      rev: Long,
                                      instant: Instant,
                                      subject: Identity)
      extends OrganizationCommand

  /**
    * An intent to create an organization.
    *
    * @param id           ID of the organization
    * @param organization representation of the organization
    * @param rev          the revision to update
    * @param instant      the instant when this command was created
    * @param subject      the subject which created this command.
    */
  final case class UpdateOrganization(id: UUID,
                                      organization: Organization,
                                      rev: Long,
                                      instant: Instant,
                                      subject: Identity)
      extends OrganizationCommand

  /**
    * An intent to deprecate an organization.
    *
    * @param id           ID of the organization
    * @param rev          the revision to deprecate
    * @param instant      the instant when this command was created
    * @param subject      the subject which created this command.
    */
  final case class DeprecateOrganization(id: UUID, rev: Long, instant: Instant, subject: Identity)
      extends OrganizationCommand
}

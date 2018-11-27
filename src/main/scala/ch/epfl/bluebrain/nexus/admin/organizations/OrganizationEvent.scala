package ch.epfl.bluebrain.nexus.admin.organizations

import java.time.Instant
import java.util.UUID

import ch.epfl.bluebrain.nexus.commons.types.identity.Identity

/**
  * Enumeration of organization event states
  */
sealed trait OrganizationEvent extends Product with Serializable {

  /**
    * @return the revision that this event generated
    */
  def rev: Long

  /**
    * @return the instant when this event was created
    */
  def instant: Instant

  /**
    * @return the subject which created this event
    */
  def subject: Identity
}

object OrganizationEvent {

  /**
    * Event representing organization creation.
    *
    * @param organization the representation of the organization
    * @param uuid         the permanent identifier of the organization
    * @param rev          the revision to create
    * @param instant      the instant when this event was created
    * @param subject      the subject which created this event
    */
  final case class OrganizationCreated(organization: Organization,
                                       uuid: UUID,
                                       rev: Long,
                                       instant: Instant,
                                       subject: Identity)
      extends OrganizationEvent

  /**
    * Event representing organization update.
    *
    * @param organization the representation of the organization
    * @param rev          the update revision
    * @param instant      the instant when this event was created
    * @param subject      the subject which created this event
    */
  final case class OrganizationUpdated(organization: Organization, rev: Long, instant: Instant, subject: Identity)
      extends OrganizationEvent

  /**
    *  Event representing organization deprecation.
    *
    * @param rev          the deprecation revision
    * @param instant      the instant when this event was created
    * @param subject      the subject which created this event
    */
  final case class OrganizationDeprecated(rev: Long, instant: Instant, subject: Identity) extends OrganizationEvent
}

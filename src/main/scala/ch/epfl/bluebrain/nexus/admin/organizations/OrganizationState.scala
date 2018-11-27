package ch.epfl.bluebrain.nexus.admin.organizations
import java.time.Instant
import java.util.UUID

import cats.effect.Async
import ch.epfl.bluebrain.nexus.admin.config.AppConfig.HttpConfig
import ch.epfl.bluebrain.nexus.admin.config.Vocabulary.nxv
import ch.epfl.bluebrain.nexus.admin.organizations.OrganizationCommand._
import ch.epfl.bluebrain.nexus.admin.organizations.OrganizationEvent._
import ch.epfl.bluebrain.nexus.admin.types.{ResourceF, ResourceMetadata}
import ch.epfl.bluebrain.nexus.commons.types.identity.Identity

/**
  * Enumeration of organization states.
  */
sealed trait OrganizationState extends Product with Serializable

object OrganizationState {

  /**
    * The initial (undefined) state.
    */
  final case object Initial extends OrganizationState

  /**
    * Initial organization state.
    *
    * @param organization the organization representation
    * @param uuid         the permanent identifier of the organization
    * @param rev          the organization revision
    * @param deprecated   the deprecation status of the organization
    * @param createdAt    the instant when the organization was created
    * @param updatedAt    the instant when the organization was last updated
    * @param createdBy    the identity that created the organization
    * @param updatedBy    the identity that last updated the organization
    */
  final case class Current(organization: Organization,
                           uuid: UUID,
                           rev: Long,
                           deprecated: Boolean,
                           createdAt: Instant,
                           updatedAt: Instant,
                           createdBy: Identity,
                           updatedBy: Identity)
      extends OrganizationState {

    def toResource(implicit http: HttpConfig): ResourceF[Organization] =
      ResourceF(http.baseIri + "orgs" + organization.label,
                uuid,
                rev,
                deprecated,
                Set(nxv.Organization),
                createdAt,
                createdBy,
                updatedAt,
                updatedBy,
                organization)

    def toResourceMetadata(implicit http: HttpConfig): ResourceMetadata =
      ResourceF.unit(http.baseIri + "orgs" + organization.label,
                     uuid,
                     rev,
                     deprecated,
                     Set(nxv.Organization),
                     createdAt,
                     createdBy,
                     updatedAt,
                     updatedBy,
      )
  }

  def next(state: OrganizationState, ev: OrganizationEvent): OrganizationState = (state, ev) match {
    case (Initial, OrganizationCreated(org, uuid, 1L, instant, identity)) =>
      Current(org, uuid, 1L, deprecated = false, instant, instant, identity, identity)

    case (c: Current, OrganizationUpdated(org, rev, instant, subject)) =>
      c.copy(organization = org, rev = rev, updatedAt = instant, updatedBy = subject)

    case (c: Current, OrganizationDeprecated(rev, instant, subject)) =>
      c.copy(rev = rev, deprecated = true, updatedAt = instant, updatedBy = subject)

    case (_, _) => Initial
  }

  def evaluate[F[_]: Async](state: OrganizationState, command: OrganizationCommand): F[EventOrRejection] = {
    val F = implicitly[Async[F]]

    def create(c: CreateOrganization): EventOrRejection = state match {
      case Initial if c.rev == 0L => Right(OrganizationCreated(c.organization, c.id, rev = 1L, c.instant, c.subject))
      case Initial                => Left(IncorrectRevisionProvided(0L, c.rev))
      case _                      => Left(OrganizationAlreadyExists)
    }

    def update(c: UpdateOrganization): EventOrRejection = state match {
      case Initial                      => Left(OrganizationDoesNotExist)
      case s: Current if c.rev == s.rev => Right(OrganizationUpdated(c.organization, c.rev + 1, c.instant, c.subject))
      case s: Current                   => Left(IncorrectRevisionProvided(s.rev, c.rev))
    }

    def deprecate(c: DeprecateOrganization): EventOrRejection = state match {
      case Initial                      => Left(OrganizationDoesNotExist)
      case s: Current if c.rev == s.rev => Right(OrganizationDeprecated(c.rev + 1, c.instant, c.subject))
      case s: Current                   => Left(IncorrectRevisionProvided(s.rev, c.rev))
    }

    command match {
      case c: CreateOrganization    => F.pure(create(c))
      case c: UpdateOrganization    => F.pure(update(c))
      case c: DeprecateOrganization => F.pure(deprecate(c))
    }
  }

}

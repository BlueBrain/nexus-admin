package ch.epfl.bluebrain.nexus.admin.organizations

import java.time.Clock
import java.util.UUID

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import cats.Monad
import cats.effect.{Async, ConcurrentEffect}
import cats.implicits._
import ch.epfl.bluebrain.nexus.admin.config.AppConfig
import ch.epfl.bluebrain.nexus.admin.config.AppConfig.HttpConfig
import ch.epfl.bluebrain.nexus.admin.organizations.OrganizationCommand._
import ch.epfl.bluebrain.nexus.admin.organizations.OrganizationEvent._
import ch.epfl.bluebrain.nexus.admin.organizations.OrganizationState._
import ch.epfl.bluebrain.nexus.admin.organizations.Organizations._
import ch.epfl.bluebrain.nexus.admin.types.{ResourceF, ResourceMetadata}
import ch.epfl.bluebrain.nexus.commons.types.identity.Identity
import ch.epfl.bluebrain.nexus.sourcing.Aggregate
import ch.epfl.bluebrain.nexus.sourcing.akka.{AkkaAggregate, AkkaSourcingConfig, PassivationStrategy, RetryStrategy}

/**
  * Organizations operations bundle
  */
class Organizations[F[_]](agg: Agg[F])(implicit F: Monad[F], clock: Clock, http: HttpConfig) {

  /**
    * Create an organization.
    *
    * @param organization organization to create
    * @param caller       identity of the caller performing the operation
    * @return             metadata about the organization
    */
  def create(organization: Organization)(implicit caller: Identity): F[OrganizationMetaOrRejection] =
    evaluate(CreateOrganization(organization.id, organization, rev = 0L, clock.instant(), caller))

  /**
    * Update an organization.
    *
    * @param organization organization to update
    * @param rev          the latest known revision
    * @param caller       identity of the caller performing the operation
    * @return
    */
  def update(organization: Organization, rev: Long)(implicit caller: Identity): F[OrganizationMetaOrRejection] =
    evaluate(UpdateOrganization(organization.id, organization, rev, clock.instant(), caller))

  /**
    * Deprecate an organization.
    *
    * @param id     ID of the organization to update
    * @param rev    latest known revision
    * @param caller identity of the caller performing the operation
    * @return       metadata about the organization
    */
  def deprecate(id: UUID, rev: Long)(implicit caller: Identity): F[OrganizationMetaOrRejection] =
    evaluate(DeprecateOrganization(id, rev, clock.instant(), caller))

  /**
    * Fetch an organization.
    *
    * @param id ID of the organization to fetch
    * @return   organization and metadata if it exists, None otherwise
    */
  def fetch(id: UUID): F[Option[ResourceF[Organization]]] =
    agg
      .currentState(id.toString)
      .map(stateToResource)

  /**
    * Fetch an organization by revision
    * @param id   ID of the organization to fetch
    * @param rev  revision to fetch
    * @return     organization and metadata if it exists, None otherwise
    */
  def fetch(id: UUID, rev: Long): F[Option[ResourceF[Organization]]] =
    agg
      .foldLeft[OrganizationState](id.toString, Initial) {
        case (state, event) if event.rev <= rev => next(state, event)
        case (state, _)                         => state
      }
      .map(stateToResource)

  private def evaluate(cmd: OrganizationCommand): F[OrganizationMetaOrRejection] =
    agg
      .evaluateS(cmd.id.toString, cmd)
      .map(
        _.flatMap {
          case c: Current => Right(c.toResourceMetadata)
          case Initial    => Left(OrganizationUnexpectedState(cmd.id))
        }
      )

  private def stateToResource(state: OrganizationState): Option[ResourceF[Organization]] = state match {
    case Initial    => None
    case c: Current => Some(c.toResource)
  }

}
object Organizations {

  /**
    * Construct ''Organization'' wrapped on an ''F'' type based on akka clustered [[Aggregate]].
    */
  def apply[F[_]: ConcurrentEffect](implicit cl: Clock = Clock.systemUTC(),
                                    ac: AppConfig,
                                    sc: AkkaSourcingConfig,
                                    as: ActorSystem,
                                    mt: ActorMaterializer): F[Organizations[F]] = {
    val aggF
      : F[Aggregate[F, String, OrganizationEvent, OrganizationState, OrganizationCommand, OrganizationRejection]] =
      AkkaAggregate.sharded(
        "organizations",
        Initial,
        next,
        evaluate[F],
        PassivationStrategy.immediately[OrganizationState, OrganizationCommand],
        RetryStrategy.never,
        sc,
        ac.cluster.shards
      )

    aggF.map(new Organizations(_))
  }

  /**
    * Construct ''Organizations'' wrapped on an ''F'' type based on an in memory [[Aggregate]].
    */
  def inMemory[F[_]: ConcurrentEffect](implicit cl: Clock, http: HttpConfig): F[Organizations[F]] = {
    val aggF
      : F[Aggregate[F, String, OrganizationEvent, OrganizationState, OrganizationCommand, OrganizationRejection]] =
      Aggregate.inMemory[F, String]("organizations", Initial, next, evaluate[F])
    aggF.map(new Organizations(_))
  }

  type Agg[F[_]] =
    Aggregate[F, String, OrganizationEvent, OrganizationState, OrganizationCommand, OrganizationRejection]

  type OrganizationMetaOrRejection = Either[OrganizationRejection, ResourceMetadata]
  type EventOrRejection            = Either[OrganizationRejection, OrganizationEvent]

  def next(state: OrganizationState, ev: OrganizationEvent): OrganizationState = (state, ev) match {
    case (Initial, OrganizationCreated(org, 1L, instant, identity)) =>
      Current(org, 1L, deprecated = false, instant, instant, identity, identity)

    case (c: Current, OrganizationUpdated(org, rev, instant, subject)) =>
      c.copy(organization = org, rev = rev, updatedAt = instant, updatedBy = subject)

    case (c: Current, OrganizationDeprecated(rev, instant, subject)) =>
      c.copy(rev = rev, deprecated = true, updatedAt = instant, updatedBy = subject)

    case (_, _) => Initial
  }

  def evaluate[F[_]: Async](state: OrganizationState, command: OrganizationCommand): F[EventOrRejection] = {
    val F = implicitly[Async[F]]

    def create(c: CreateOrganization): EventOrRejection = state match {
      case Initial if c.rev == 0L => Right(OrganizationCreated(c.organization, rev = 1L, c.instant, c.subject))
      case Initial                => Left(IncorrectOrganizationRevRejection(0L, c.rev))
      case _                      => Left(OrganizationAlreadyExistsRejection)
    }

    def update(c: UpdateOrganization): EventOrRejection = state match {
      case Initial                      => Left(OrganizationDoesNotExistRejection)
      case s: Current if c.rev == s.rev => Right(OrganizationUpdated(c.organization, c.rev + 1, c.instant, c.subject))
      case s: Current                   => Left(IncorrectOrganizationRevRejection(s.rev, c.rev))
    }

    def deprecate(c: DeprecateOrganization): EventOrRejection = state match {
      case Initial                      => Left(OrganizationDoesNotExistRejection)
      case s: Current if c.rev == s.rev => Right(OrganizationDeprecated(c.rev + 1, c.instant, c.subject))
      case s: Current                   => Left(IncorrectOrganizationRevRejection(s.rev, c.rev))
    }

    command match {
      case c: CreateOrganization    => F.pure(create(c))
      case c: UpdateOrganization    => F.pure(update(c))
      case c: DeprecateOrganization => F.pure(deprecate(c))
    }
  }

}

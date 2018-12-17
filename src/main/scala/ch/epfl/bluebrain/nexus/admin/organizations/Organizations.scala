package ch.epfl.bluebrain.nexus.admin.organizations

import java.time.Clock
import java.util.UUID

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import cats.MonadError
import cats.effect.{Async, ConcurrentEffect}
import cats.implicits._
import ch.epfl.bluebrain.nexus.admin.config.AppConfig
import ch.epfl.bluebrain.nexus.admin.config.AppConfig.HttpConfig
import ch.epfl.bluebrain.nexus.admin.exceptions.AdminError.UnexpectedState
import ch.epfl.bluebrain.nexus.admin.index.Index
import ch.epfl.bluebrain.nexus.admin.organizations.OrganizationCommand._
import ch.epfl.bluebrain.nexus.admin.organizations.OrganizationEvent._
import ch.epfl.bluebrain.nexus.admin.organizations.Organizations.next
import ch.epfl.bluebrain.nexus.admin.organizations.OrganizationRejection._
import ch.epfl.bluebrain.nexus.admin.organizations.OrganizationState._
import ch.epfl.bluebrain.nexus.admin.types.ResourceF
import ch.epfl.bluebrain.nexus.commons.types.search.Pagination
import ch.epfl.bluebrain.nexus.commons.types.search.QueryResult.UnscoredQueryResult
import ch.epfl.bluebrain.nexus.commons.types.search.QueryResults.UnscoredQueryResults
import ch.epfl.bluebrain.nexus.iam.client.types.Identity.Subject
import ch.epfl.bluebrain.nexus.sourcing.akka.{AkkaAggregate, AkkaSourcingConfig, PassivationStrategy, RetryStrategy}

/**
  * Organizations operations bundle
  */
class Organizations[F[_]](agg: Agg[F], index: Index[F])(implicit F: MonadError[F, Throwable],
                                                        clock: Clock,
                                                        http: HttpConfig) {

  /**
    * Create an organization.
    *
    * @param organization organization to create
    * @param caller       identity of the caller performing the operation
    * @return             metadata about the organization
    */
  def create(organization: Organization)(implicit caller: Subject): F[OrganizationMetaOrRejection] =
    index.getOrganization(organization.label).flatMap {
      case None =>
        evaluateAndUpdateIndex(CreateOrganization(UUID.randomUUID(), rev = 0L, organization, clock.instant(), caller),
                               organization)
      case Some(_) => F.pure(Left(OrganizationExists))
    }

  /**
    * Update an organization.
    *
    * @param label        label of the organization to update
    * @param organization the updated organization
    * @param rev          the latest known revision
    * @param caller       identity of the caller performing the operation
    * @return
    */
  def update(label: String, organization: Organization, rev: Long)(
      implicit caller: Subject): F[OrganizationMetaOrRejection] =
    index.getOrganization(label).flatMap {
      case Some(org) =>
        evaluateAndUpdateIndex(UpdateOrganization(org.uuid, rev, organization, clock.instant(), caller), organization)
      case None => F.pure(Left(OrganizationNotFound))
    }

  /**
    * Deprecate an organization.
    *
    * @param label  label of the organization to update
    * @param rev    latest known revision
    * @param caller identity of the caller performing the operation
    * @return       metadata about the organization
    */
  def deprecate(label: String, rev: Long)(implicit caller: Subject): F[OrganizationMetaOrRejection] =
    index.getOrganization(label).flatMap {
      case Some(org) =>
        evaluateAndUpdateIndex(DeprecateOrganization(org.uuid, rev, clock.instant(), caller), org.value)
      case None => F.pure(Left(OrganizationNotFound))
    }

  /**
    * Fetch an organization
    *
    * @param label  label of the organization to fetch
    * @param rev    optional revision to fetch
    * @return       organization and metadata if it exists, None otherwise
    */
  def fetch(label: String, rev: Option[Long] = None): F[Option[ResourceF[Organization]]] = rev match {
    case None =>
      index.getOrganization(label)
    case Some(value) =>
      index.getOrganization(label).flatMap {
        case Some(org) =>
          agg
            .foldLeft[OrganizationState](org.uuid.toString, Initial) {
              case (state, event) if event.rev <= value => next(state, event)
              case (state, _)                           => state
            }
            .map(stateToResource)
        case None => F.pure(None)
      }
  }

  /**
    * Fetch organization by UUID.
    *
    * @param  id of the organization.
    * @return organization and metadata if it exists, None otherwise
    */
  def fetch(id: UUID): F[Option[ResourceF[Organization]]] =
    agg.currentState(id.toString).map(stateToResource)

  /**
    * Lists all indexed organizations.
    *
    * @param pagination the pagination settings
    * @return a paginated results list
    */
  def list(pagination: Pagination): F[UnscoredQueryResults[ResourceF[Organization]]] =
    index.listOrganizations(pagination).map { orgs =>
      val results = orgs.map(org => UnscoredQueryResult(org))
      UnscoredQueryResults(orgs.size.toLong, results)
    }

  private def evaluate(cmd: OrganizationCommand): F[OrganizationMetaOrRejection] =
    agg
      .evaluateS(cmd.id.toString, cmd)
      .flatMap {
        case Right(c: Current) => F.pure(Right(c.toResourceMetadata))
        case Right(Initial)    => F.raiseError(UnexpectedState(cmd.id.toString))
        case Left(rejection)   => F.pure(Left(rejection))
      }

  private def evaluateAndUpdateIndex(command: OrganizationCommand,
                                     organization: Organization): F[OrganizationMetaOrRejection] =
    evaluate(command).flatMap {
      case Right(metadata) =>
        index.updateOrganization(metadata.map(_ => organization)) *> F.pure(Right(metadata))
      case Left(rej) => F.pure(Left(rej))
    }

  private def stateToResource(state: OrganizationState): Option[ResourceF[Organization]] = state match {
    case Initial    => None
    case c: Current => Some(c.toResource)
  }

}
object Organizations {

  /**
    * Construct ''Organization'' wrapped on an ''F'' type based on akka clustered [[Agg]].
    */
  def apply[F[_]: ConcurrentEffect](index: Index[F])(implicit cl: Clock = Clock.systemUTC(),
                                                     ac: AppConfig,
                                                     sc: AkkaSourcingConfig,
                                                     as: ActorSystem,
                                                     mt: ActorMaterializer): F[Organizations[F]] = {
    implicit val http: HttpConfig = ac.http
    val aggF: F[Agg[F]] =
      AkkaAggregate.sharded(
        "organizations",
        Initial,
        next,
        evaluate[F],
        PassivationStrategy.never[OrganizationState, OrganizationCommand],
        RetryStrategy.never,
        sc,
        ac.cluster.shards
      )

    aggF.map(new Organizations(_, index))
  }

  private[organizations] def next(state: OrganizationState, ev: OrganizationEvent): OrganizationState =
    (state, ev) match {
      case (Initial, OrganizationCreated(uuid, 1L, org, instant, identity)) =>
        Current(uuid, 1L, org, deprecated = false, instant, identity, instant, identity)

      case (c: Current, OrganizationUpdated(_, rev, org, instant, subject)) =>
        c.copy(rev = rev, organization = org, updatedAt = instant, updatedBy = subject)

      case (c: Current, OrganizationDeprecated(_, rev, instant, subject)) =>
        c.copy(rev = rev, deprecated = true, updatedAt = instant, updatedBy = subject)

      case (_, _) => Initial
    }

  private[organizations] def evaluate[F[_]](state: OrganizationState, command: OrganizationCommand)(
      implicit F: Async[F]): F[EventOrRejection] = {

    def create(c: CreateOrganization): EventOrRejection = state match {
      case Initial if c.rev == 0L => Right(OrganizationCreated(c.id, rev = 1L, c.organization, c.instant, c.subject))
      case Initial                => Left(IncorrectRev(0L, c.rev))
      case _                      => Left(OrganizationExists)
    }

    def update(c: UpdateOrganization): EventOrRejection = state match {
      case Initial => Left(OrganizationNotFound)
      case s: Current if c.rev == s.rev =>
        Right(OrganizationUpdated(c.id, c.rev + 1, c.organization, c.instant, c.subject))
      case s: Current => Left(IncorrectRev(s.rev, c.rev))
    }

    def deprecate(c: DeprecateOrganization): EventOrRejection = state match {
      case Initial                      => Left(OrganizationNotFound)
      case s: Current if c.rev == s.rev => Right(OrganizationDeprecated(c.id, c.rev + 1, c.instant, c.subject))
      case s: Current                   => Left(IncorrectRev(s.rev, c.rev))
    }

    command match {
      case c: CreateOrganization    => F.pure(create(c))
      case c: UpdateOrganization    => F.pure(update(c))
      case c: DeprecateOrganization => F.pure(deprecate(c))
    }
  }

}

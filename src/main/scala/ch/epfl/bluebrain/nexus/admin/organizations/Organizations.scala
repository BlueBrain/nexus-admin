package ch.epfl.bluebrain.nexus.admin.organizations

import java.time.Clock
import java.util.UUID

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import cats.MonadError
import cats.effect.ConcurrentEffect
import cats.implicits._
import ch.epfl.bluebrain.nexus.admin.config.AppConfig
import ch.epfl.bluebrain.nexus.admin.config.AppConfig.HttpConfig
import ch.epfl.bluebrain.nexus.admin.exceptions.UnexpectedState
import ch.epfl.bluebrain.nexus.admin.organizations.OrganizationCommand._
import ch.epfl.bluebrain.nexus.admin.organizations.OrganizationState._
import ch.epfl.bluebrain.nexus.admin.types.ResourceF
import ch.epfl.bluebrain.nexus.commons.types.identity.Identity
import ch.epfl.bluebrain.nexus.sourcing.Aggregate
import ch.epfl.bluebrain.nexus.sourcing.akka.{AkkaAggregate, AkkaSourcingConfig, PassivationStrategy, RetryStrategy}

/**
  * Organizations operations bundle
  */
class Organizations[F[_]](agg: Agg[F])(implicit F: MonadError[F, Throwable], clock: Clock, http: HttpConfig) {

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
      .flatMap {
        case Right(c: Current) => F.pure(Right(c.toResourceMetadata))
        case Right(Initial)    => F.raiseError(new UnexpectedState(cmd.id.toString))
        case Left(rejection)   => F.pure(Left(rejection))
      }

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
    implicit val http: HttpConfig = ac.http
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
}

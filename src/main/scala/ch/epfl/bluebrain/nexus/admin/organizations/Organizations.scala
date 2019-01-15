package ch.epfl.bluebrain.nexus.admin.organizations

import java.time.Clock
import java.util.UUID

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import cats.MonadError
import cats.effect.{Async, ConcurrentEffect, Timer}
import cats.implicits._
import ch.epfl.bluebrain.nexus.admin.config.AppConfig
import ch.epfl.bluebrain.nexus.admin.config.AppConfig.HttpConfig
import ch.epfl.bluebrain.nexus.admin.exceptions.AdminError.UnexpectedState
import ch.epfl.bluebrain.nexus.admin.index.OrganizationCache
import ch.epfl.bluebrain.nexus.admin.organizations.OrganizationCommand._
import ch.epfl.bluebrain.nexus.admin.organizations.OrganizationEvent._
import ch.epfl.bluebrain.nexus.admin.organizations.OrganizationRejection._
import ch.epfl.bluebrain.nexus.admin.organizations.OrganizationState._
import ch.epfl.bluebrain.nexus.admin.organizations.Organizations.next
import ch.epfl.bluebrain.nexus.commons.types.search.Pagination
import ch.epfl.bluebrain.nexus.commons.types.search.QueryResults.UnscoredQueryResults
import ch.epfl.bluebrain.nexus.iam.client.IamClient
import ch.epfl.bluebrain.nexus.iam.client.types._
import ch.epfl.bluebrain.nexus.iam.client.types.Identity.Subject
import ch.epfl.bluebrain.nexus.rdf.Iri.Path._
import ch.epfl.bluebrain.nexus.sourcing.akka.AkkaAggregate

/**
  * Organizations operations bundle
  */
class Organizations[F[_]](agg: Agg[F], index: OrganizationCache[F], iamClient: IamClient[F])(
    implicit F: MonadError[F, Throwable],
    clock: Clock,
    http: HttpConfig,
    iamCredentials: Option[AuthToken],
    ownerPermissions: Set[Permission]
) {

  /**
    * Create an organization.
    *
    * @param organization organization to create
    * @param caller       identity of the caller performing the operation
    * @return             metadata about the organization
    */
  def create(organization: Organization)(implicit caller: Subject): F[OrganizationMetaOrRejection] =
    index.getBy(organization.label).flatMap {
      case Some(_) => F.pure(Left(OrganizationExists))
      case None =>
        val cmd =
          CreateOrganization(UUID.randomUUID, organization.label, organization.description, clock.instant, caller)
        evalAndUpdateIndex(cmd, organization) <* setOwnerPermissions(organization.label, caller)
    }

  def setPermissions(orgLabel: String, acls: AccessControlLists, subject: Subject): F[Unit] = {

    val currentPermissions = acls.filter(Set(subject)).value.foldLeft(Set.empty[Permission]) {
      case (acc, (_, acl)) => acc ++ acl.value.permissions
    }
    val orgAcl = acls.value.get(/ + orgLabel).map(_.value.value).getOrElse(Map.empty)
    val rev    = acls.value.get(/ + orgLabel).map(_.rev)

    if (ownerPermissions.subsetOf(currentPermissions)) F.unit
    else iamClient.putAcls(/ + orgLabel, AccessControlList(orgAcl + (subject -> ownerPermissions)), rev)

  }

  private def setOwnerPermissions(orgLabel: String, subject: Subject): F[Unit] = {
    for {
      acls <- iamClient.acls(/ + orgLabel, ancestors = true, self = false)
      _    <- setPermissions(orgLabel, acls, subject)
    } yield ()
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
    index.getBy(label).flatMap {
      case Some(org) =>
        evalAndUpdateIndex(
          UpdateOrganization(org.uuid, rev, organization.label, organization.description, clock.instant, caller),
          organization)
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
    index.getBy(label).flatMap {
      case Some(org) =>
        evalAndUpdateIndex(DeprecateOrganization(org.uuid, rev, clock.instant(), caller), org.value)
      case None => F.pure(Left(OrganizationNotFound))
    }

  /**
    * Fetch an organization
    *
    * @param label  label of the organization to fetch
    * @param rev    optional revision to fetch
    * @return       organization and metadata if it exists, None otherwise
    */
  def fetch(label: String, rev: Option[Long] = None): F[Option[OrganizationResource]] = rev match {
    case None =>
      index.getBy(label)
    case Some(value) =>
      index.getBy(label).flatMap {
        case Some(org) =>
          val stateF = agg.foldLeft[OrganizationState](org.uuid.toString, Initial) {
            case (state, event) if event.rev <= value => next(state, event)
            case (state, _)                           => state
          }
          stateF.map(stateToResource)
        case None => F.pure(None)
      }
  }

  /**
    * Fetch organization by UUID.
    *
    * @param  id of the organization.
    * @return organization and metadata if it exists, None otherwise
    */
  def fetch(id: UUID): F[Option[OrganizationResource]] =
    agg.currentState(id.toString).map(stateToResource)

  /**
    * Lists all indexed organizations.
    *
    * @param pagination the pagination settings
    * @return a paginated results list
    */
  def list(pagination: Pagination): F[UnscoredQueryResults[OrganizationResource]] =
    index.list(pagination)

  private def eval(cmd: OrganizationCommand): F[OrganizationMetaOrRejection] =
    agg.evaluateS(cmd.id.toString, cmd).flatMap {
      case Right(c: Current) => F.pure(Right(c.toResourceMetadata))
      case Right(Initial)    => F.raiseError(UnexpectedState(cmd.id.toString))
      case Left(rejection)   => F.pure(Left(rejection))
    }

  private def evalAndUpdateIndex(command: OrganizationCommand,
                                 organization: Organization): F[OrganizationMetaOrRejection] =
    eval(command).flatMap {
      case Right(metadata) => index.replace(metadata.uuid, metadata.withValue(organization)) *> F.pure(Right(metadata))
      case Left(rej)       => F.pure(Left(rej))
    }

  private def stateToResource(state: OrganizationState): Option[OrganizationResource] = state match {
    case Initial    => None
    case c: Current => Some(c.toResource)
  }

}
object Organizations {

  /**
    * Construct ''Organization'' wrapped on an ''F'' type based on akka clustered [[Agg]].
    */
  def apply[F[_]: ConcurrentEffect: Timer](index: OrganizationCache[F], iamClient: IamClient[F], appConfig: AppConfig)(
      implicit cl: Clock = Clock.systemUTC(),
      as: ActorSystem,
      mt: ActorMaterializer): F[Organizations[F]] = {
    implicit val http: HttpConfig                  = appConfig.http
    implicit val iamCredentials: Option[AuthToken] = appConfig.serviceAccount.credentials
    implicit val ownerPermissions: Set[Permission] = appConfig.permissions.ownerPermissions
    val aggF: F[Agg[F]] =
      AkkaAggregate.sharded(
        "organizations",
        Initial,
        next,
        evaluate[F],
        appConfig.sourcing.passivationStrategy(),
        appConfig.sourcing.retryStrategy,
        appConfig.sourcing.akkaSourcingConfig,
        appConfig.cluster.shards
      )

    aggF.map(new Organizations(_, index, iamClient))
  }

  private[organizations] def next(state: OrganizationState, ev: OrganizationEvent): OrganizationState =
    (state, ev) match {
      case (Initial, OrganizationCreated(uuid, label, desc, instant, identity)) =>
        Current(uuid, 1L, label, desc, deprecated = false, instant, identity, instant, identity)

      case (c: Current, OrganizationUpdated(_, rev, label, desc, instant, subject)) =>
        c.copy(rev = rev, label = label, description = desc, updatedAt = instant, updatedBy = subject)

      case (c: Current, OrganizationDeprecated(_, rev, instant, subject)) =>
        c.copy(rev = rev, deprecated = true, updatedAt = instant, updatedBy = subject)

      case (_, _) => Initial
    }

  private[organizations] def evaluate[F[_]](state: OrganizationState, command: OrganizationCommand)(
      implicit F: Async[F]): F[EventOrRejection] = {

    def create(c: CreateOrganization): EventOrRejection = state match {
      case Initial => Right(OrganizationCreated(c.id, c.label, c.description, c.instant, c.subject))
      case _       => Left(OrganizationExists)
    }

    def update(c: UpdateOrganization): EventOrRejection = state match {
      case Initial => Left(OrganizationNotFound)
      case s: Current if c.rev == s.rev =>
        Right(OrganizationUpdated(c.id, c.rev + 1, c.label, c.description, c.instant, c.subject))
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

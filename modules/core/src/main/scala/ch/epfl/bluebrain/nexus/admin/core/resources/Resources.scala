package ch.epfl.bluebrain.nexus.admin.core.resources

import java.time.Clock

import cats._
import cats.implicits._
import cats.MonadError
import ch.epfl.bluebrain.nexus.admin.core.CallerCtx
import ch.epfl.bluebrain.nexus.admin.core.Fault.{CommandRejected, Unexpected}
import ch.epfl.bluebrain.nexus.admin.core.resources.ResourceCommand._
import ch.epfl.bluebrain.nexus.admin.core.resources.ResourceRejection._
import ch.epfl.bluebrain.nexus.admin.core.resources.ResourceState._
import ch.epfl.bluebrain.nexus.admin.core.resources.Resources.Agg
import ch.epfl.bluebrain.nexus.admin.core.types.Ref._
import ch.epfl.bluebrain.nexus.admin.core.types.RefVersioned
import ch.epfl.bluebrain.nexus.admin.ld.IdResolvable
import ch.epfl.bluebrain.nexus.admin.refined.ld.DecomposableId
import ch.epfl.bluebrain.nexus.admin.refined.ld.DecomposableUri._
import ch.epfl.bluebrain.nexus.admin.refined.permissions._
import ch.epfl.bluebrain.nexus.sourcing.Aggregate
import com.github.ghik.silencer.silent
import io.circe.Json
import journal.Logger

/**
  * Bundles operations that can be performed against a resource using the underlying persistence abstraction.
  *
  * @param agg    the aggregate definition
  * @param F      a MonadError typeclass instance for ''F[_]''
  * @param logger the logger
  * @param clock  the clock used to issue instants
  * @tparam F the monadic effect type
  * @tparam A the generic type of the id's ''reference''
  */
class Resources[F[_], A: IdResolvable](agg: Agg[F])(implicit
                                                    F: MonadError[F, Throwable],
                                                    logger: Logger,
                                                    clock: Clock) {

  /**
    * Certain validation to take place during creation operations.
    *
    * @param id    the identifier of the resource
    * @param value the json payload of the resource
    * @return () or the appropriate rejection in the ''F'' context
    */
  @silent
  def validateCreate(id: A, value: Json): F[Unit] = F.pure(())

  /**
    * Certain validation to take place during update operations.
    *
    * @param id    the identifier of the resource
    * @param value the json payload of the resource
    * @return () or the appropriate rejection in the ''F'' context
    */
  @silent
  def validateUpdate(id: A, value: Json): F[Unit] = F.pure(())

  /**
    * Asserts the resource exists and it allows modifications on children resources.
    *
    * @param persId the unique persistence ID of the resource
    * @return () or the appropriate rejection in the ''F'' context
    */
  def validateUnlocked(persId: String): F[Unit] =
    agg.currentState(persId) flatMap {
      case Initial                   => F.raiseError(CommandRejected(ParentResourceDoesNotExists))
      case Current(_, _, _, _, true) => F.raiseError(CommandRejected(ResourceIsDeprecated))
      case _                         => F.pure(())
    }

  /**
    * Attempts to create a new resource instance.
    *
    * @param id     the identifier of the resource
    * @param value  the json payload of the resource
    * @param tags   the tags added to the consequent [[ResourceEvent]] which might be created as a result of this operation
    * @param persId the unique persistence ID of the resource
    * @return a [[RefVersioned]] instance wrapped in the abstract ''F[_]'' type
    *         if successful, or a [[ch.epfl.bluebrain.nexus.admin.core.Fault]] wrapped within ''F[_]'' otherwise
    */
  def create(id: A, value: Json)(tags: Set[String], persId: String)(implicit ctx: CallerCtx,
                                                                    hasPerms: F[HasOwnProjects]): F[RefVersioned[A]] =
    for {
      _ <- (validateCreate(id, value), hasPerms).mapN((_, _) => ())
      r <- evaluate(CreateResource(id, ctx.meta, tags + persId, value), persId, s"Create res '$id'")
    } yield RefVersioned(id, r.rev)

  /**
    * Attempts to update an existing resource instance with a new json payload.
    *
    * @param id     the identifier of the resource
    * @param rev    the last known revision of the resource instance
    * @param value  the json payload of the resource
    * @param tags   the tags added to the consequent [[ResourceEvent]] which might be created as a result of this operation
    * @param persId the unique persistence ID of the resource
    * @return a [[RefVersioned]] instance wrapped in the abstract ''F[_]'' type
    *         if successful, or a [[ch.epfl.bluebrain.nexus.admin.core.Fault]] wrapped within ''F[_]'' otherwise
    */
  def update(id: A, rev: Long, value: Json)(tags: Set[String], persId: String)(
      implicit ctx: CallerCtx,
      hasPerms: F[HasWriteProjects]): F[RefVersioned[A]] =
    for {
      _ <- (validateUpdate(id, value), hasPerms).mapN((_, _) => ())
      r <- evaluate(UpdateResource(id, rev, ctx.meta, tags + persId, value), persId, s"Update res '$id'")
    } yield RefVersioned(id, r.rev)

  /**
    * Attempts to deprecate a resource locking it for further changes and blocking any attempts to create instances conforming to its
    * definition.
    *
    * @param id     the identifier of the resource
    * @param rev    the last known revision of the resource instance
    * @param tags   the tags added to the consequent [[ResourceEvent]] which might be created as a result of this operation
    * @param persId the unique persistence ID of the resource
    * @return a [[RefVersioned]] instance wrapped in the abstract ''F[_]'' type
    *         if successful, or a [[ch.epfl.bluebrain.nexus.admin.core.Fault]] wrapped within ''F[_]'' otherwise
    */
  def deprecate(id: A, rev: Long)(tags: Set[String], persId: String)(
      implicit ctx: CallerCtx,
      hasPerms: F[HasWriteProjects]): F[RefVersioned[A]] =
    (hasPerms, evaluate(DeprecateResource(id, rev, ctx.meta, tags + persId), persId, s"Deprecate res '$id'"))
      .mapN((_, r) => RefVersioned(id, r.rev))

  /**
    * Queries the system for the latest revision of the resource identified by the argument ''persId''.
    * The (in)existence of the resource is represented by the [[scala.Option]] type wrapped within the ''F[_]'' context.
    *
    * @param id     the identifier of the resource
    * @param persId the unique persistence ID of the resource
    * @return an optional [[Resource]] instance wrapped in the
    *         abstract ''F[_]'' type if successful, or a [[ch.epfl.bluebrain.nexus.admin.core.Fault]] wrapped within
    *         ''F[_]'' otherwise
    */
  def fetch(id: A)(persId: String)(implicit hasPerms: F[HasReadProjects]): F[Option[Resource[A]]] =
    hasPerms.flatMap { _ =>
      agg.currentState(persId).map {
        case Initial    => None
        case c: Current => Some(Resource(id, c.rev, c.value, c.deprecated))
      }
    }

  /**
    * Queries the system for a specific ''revision'' of the resource identified by the argument ''persId''.
    * The (in)existence of the resource is represented by the [[scala.Option]] type wrapped within the ''F[_]'' context.
    *
    * @param id     the identifier of the resource
    * @param rev    the revision attempted to be fetched
    * @param persId the unique persistence ID of the resource
    * @return an optional [[Resource]] instance wrapped in the
    *         abstract ''F[_]'' type if successful, or a [[ch.epfl.bluebrain.nexus.admin.core.Fault]] wrapped within
    *         ''F[_]'' otherwise
    */
  def fetch(id: A, rev: Long)(persId: String)(implicit hasPerms: F[HasReadProjects]): F[Option[Resource[A]]] =
    hasPerms.flatMap { _ =>
      stateAt(persId, rev).map {
        case c: Current if c.rev == rev => Some(Resource(id, c.rev, c.value, c.deprecated))
        case _                          => None
      }
    }

  private def stateAt(persId: String, rev: Long): F[ResourceState] =
    agg.foldLeft[ResourceState](persId, Initial) {
      case (state, ev) if ev.rev <= rev => next(state, ev)
      case (state, _)                   => state
    }

  private def evaluate(cmd: ResourceCommand, persId: String, intent: => String): F[Current] =
    F.pure {
      logger.debug(s"$intent: evaluating command '$cmd''")
    } flatMap { _ =>
      agg.eval(persId, cmd)
    } flatMap {
      case Left(rejection) =>
        logger.debug(s"$intent: command '$cmd' was rejected due to '$rejection'")
        F.raiseError(CommandRejected(rejection))
      // $COVERAGE-OFF$
      case Right(s @ Initial) =>
        logger.error(s"$intent: command '$cmd' evaluation failed, received an '$s' state")
        F.raiseError(Unexpected(s"Unexpected Initial state as outcome of evaluating command '$cmd'"))
      // $COVERAGE-ON$
      case Right(state: Current) =>
        logger.debug(s"$intent: command '$cmd' evaluation succeeded, generated state: '$state'")
        F.pure(state)
    }

  private implicit def toDecomposableId(id: A): DecomposableId =
    (id.prefixValue, id.reference).decomposableId
}

object Resources {

  type Agg[F[_]] = Aggregate[F] {
    type Identifier = String
    type Event      = ResourceEvent
    type State      = ResourceState
    type Command    = ResourceCommand
    type Rejection  = ResourceRejection
  }
}

package ch.epfl.bluebrain.nexus.admin.core.resources

import cats._
import cats.implicits._
import cats.{MonadError, Show}
import ch.epfl.bluebrain.nexus.admin.core.CallerCtx
import ch.epfl.bluebrain.nexus.admin.core.Fault.{CommandRejected, Unexpected}
import ch.epfl.bluebrain.nexus.admin.core.resources.ResourceCommand._
import ch.epfl.bluebrain.nexus.admin.core.resources.ResourceRejection._
import ch.epfl.bluebrain.nexus.admin.core.resources.ResourceState._
import ch.epfl.bluebrain.nexus.admin.core.resources.Resources.Agg
import ch.epfl.bluebrain.nexus.admin.core.types.PrefixValueOps._
import ch.epfl.bluebrain.nexus.admin.core.types.Ref._
import ch.epfl.bluebrain.nexus.admin.core.types.RefVersioned
import ch.epfl.bluebrain.nexus.admin.ld.{IdRef, IdResolvable}
import ch.epfl.bluebrain.nexus.admin.refined.permissions._
import ch.epfl.bluebrain.nexus.sourcing.Aggregate
import io.circe.Json
import journal.Logger

abstract class Resources[F[_], A: IdResolvable: Show](agg: Agg[F])(implicit
                                                                   F: MonadError[F, Throwable],
                                                                   logger: Logger) {

  def toPersistedId(ref: IdRef): String =
    s"${ref.reference.value}-${ref.prefixValue.host.hashCode.abs.toString.take(5)}"

  def validate(id: A, value: Json): F[Unit]

  def validateUnlocked(id: A): F[Unit] =
    agg.currentState(toPersistedId(id)) flatMap {
      case Initial                   => F.raiseError(CommandRejected(ParentResourceDoesNotExists))
      case Current(_, _, _, _, true) => F.raiseError(CommandRejected(ResourceIsDeprecated))
      case _                         => F.pure(())
    }

  def create(id: A, value: Json, tags: Set[String] = Set.empty)(implicit ctx: CallerCtx,
                                                                validatePerms: F[HasOwnProjects]): F[RefVersioned[A]] =
    for {
      _ <- (validate(id, value), validatePerms).mapN((_, _) => ())
      r <- evaluate(CreateResource(id, ctx.meta, tags + toPersistedId(id), value), s"Create res '$id'")
    } yield RefVersioned(id, r.rev)

  def update(id: A, rev: Long, value: Json, tags: Set[String] = Set.empty)(
      implicit ctx: CallerCtx,
      validatePerms: F[HasWriteProjects]): F[RefVersioned[A]] =
    for {
      _ <- (validate(id, value), validatePerms).mapN((_, _) => ())
      r <- evaluate(UpdateResource(id, rev, ctx.meta, tags + toPersistedId(id), value), s"Update res '$id'")
    } yield RefVersioned(id, r.rev)

  def deprecate(id: A, rev: Long, tags: Set[String] = Set.empty)(
      implicit ctx: CallerCtx,
      validatePerms: F[HasWriteProjects]): F[RefVersioned[A]] =
    (validatePerms, evaluate(DeprecateResource(id, rev, ctx.meta, tags + toPersistedId(id)), s"Deprecate res '$id'"))
      .mapN((_, r) => RefVersioned(id, r.rev))

  def fetch(id: A): F[Option[Resource[A]]] =
    agg.currentState(toPersistedId(id)).map {
      case Initial    => None
      case c: Current => Some(Resource(id, c.rev, c.value, c.deprecated))
    }

  def fetch(id: A, rev: Long): F[Option[Resource[A]]] =
    stateAt(id, rev).map {
      case c: Current if c.rev == rev => Some(Resource(id, c.rev, c.value, c.deprecated))
      case _                          => None
    }

  private def stateAt(id: A, rev: Long): F[ResourceState] =
    agg.foldLeft[ResourceState](toPersistedId(id), Initial) {
      case (state, ev) if ev.rev <= rev => next(state, ev)
      case (state, _)                   => state
    }

  private def evaluate(cmd: ResourceCommand, intent: => String): F[Current] =
    F.pure {
      logger.debug(s"$intent: evaluating command '$cmd''")
    } flatMap { _ =>
      agg.eval(toPersistedId(cmd.id), cmd)
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

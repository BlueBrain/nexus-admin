package ch.epfl.bluebrain.nexus.admin.core.projects

import java.time.Clock

import cats.MonadError
import cats.syntax.flatMap._
import ch.epfl.bluebrain.nexus.admin.core.CallerCtx
import ch.epfl.bluebrain.nexus.admin.core.Fault.Unexpected
import ch.epfl.bluebrain.nexus.admin.core.config.AppConfig._
import ch.epfl.bluebrain.nexus.admin.core.projects.Project._
import ch.epfl.bluebrain.nexus.admin.core.projects.Projects._
import ch.epfl.bluebrain.nexus.admin.core.resources.Resources.Agg
import ch.epfl.bluebrain.nexus.admin.core.resources.{Resource, Resources}
import ch.epfl.bluebrain.nexus.admin.core.types.NamespaceOps._
import ch.epfl.bluebrain.nexus.admin.core.types.Ref._
import ch.epfl.bluebrain.nexus.admin.core.types.RefVersioned
import ch.epfl.bluebrain.nexus.admin.ld.{IdRef, IdResolvable}
import ch.epfl.bluebrain.nexus.admin.refined.permissions._
import ch.epfl.bluebrain.nexus.admin.refined.project._
import io.circe.Json
import io.circe.syntax._
import journal.Logger

/**
  * Bundles operations that can be performed against a project using the underlying persistence abstraction.
  *
  * @param resources the underlying generic resource operations
  * @param F         a MonadError typeclass instance for ''F[_]''
  * @tparam F the monadic effect type
  */
class Projects[F[_]](resources: Resources[F, ProjectReference])(implicit F: MonadError[F, Throwable],
                                                                idRes: IdResolvable[ProjectReference],
                                                                config: ProjectsConfig) {

  private val tags = Set("project")

  /**
    * Attempts to create a new project instance.
    *
    * @param reference the name of the project
    * @param value     the payload of the project
    * @return a [[RefVersioned]] instance wrapped in the abstract ''F[_]'' type
    *         if successful, or a [[ch.epfl.bluebrain.nexus.admin.core.Fault]] wrapped within ''F[_]'' otherwise
    */
  def create(reference: ProjectReference, value: Json)(implicit ctx: CallerCtx,
                                                       perms: HasOwnProjects): F[RefVersioned[ProjectReference]] =
    resources.create(reference, value)(tags, reference.toPersId)

  /**
    * Attempts to update an existing project instance with a new json payload.
    *
    * @param reference the name of the project
    * @param rev       the last known revision of the project instance
    * @param value     the payload of the project
    * @return a [[RefVersioned]] instance wrapped in the abstract ''F[_]'' type
    *         if successful, or a [[ch.epfl.bluebrain.nexus.admin.core.Fault]] wrapped within ''F[_]'' otherwise
    */
  def update(reference: ProjectReference, rev: Long, value: Json)(
      implicit ctx: CallerCtx,
      perms: HasWriteProjects): F[RefVersioned[ProjectReference]] =
    resources.update(reference, rev, value.asJson)(tags, reference.toPersId)

  /**
    * Attempts to deprecate a project locking it for further changes and blocking any attempts to create instances conforming to its
    * definition.
    *
    * @param reference the name of the project
    * @param rev    the last known revision of the project instance
    * @return a [[RefVersioned]] instance wrapped in the abstract ''F[_]'' type
    *         if successful, or a [[ch.epfl.bluebrain.nexus.admin.core.Fault]] wrapped within ''F[_]'' otherwise
    */
  def deprecate(reference: ProjectReference, rev: Long)(implicit ctx: CallerCtx,
                                                        perms: HasWriteProjects): F[RefVersioned[ProjectReference]] =
    resources.deprecate(reference, rev)(tags, reference.toPersId)

  /**
    * Queries the system for the latest revision of the project identified by the argument ''reference''.
    * The (in)existence of the project is represented by the [[scala.Option]] type wrapped within the ''F[_]'' context.
    *
    * @param reference the name of the project
    * @return an optional [[Project]] instance wrapped in the
    *         abstract ''F[_]'' type if successful, or a [[ch.epfl.bluebrain.nexus.admin.core.Fault]] wrapped within
    *         ''F[_]'' otherwise
    */
  def fetch(reference: ProjectReference)(implicit perms: HasReadProjects): F[Option[Project]] =
    resources.fetch(reference)(reference.toPersId)

  /**
    * Queries the system for a specific ''revision'' of the project identified by the argument ''reference''.
    * The (in)existence of the represented is represented by the [[scala.Option]] type wrapped within the ''F[_]'' context.
    *
    * @param reference the name of the project
    * @return an optional [[Project]] instance wrapped in the
    *         abstract ''F[_]'' type if successful, or a [[ch.epfl.bluebrain.nexus.admin.core.Fault]] wrapped within
    *         ''F[_]'' otherwise
    */
  def fetch(reference: ProjectReference, rev: Long)(implicit perms: HasReadProjects): F[Option[Project]] =
    resources.fetch(reference, rev)(reference.toPersId)

  /**
    * Asserts the project exists and it allows modifications on children resources.
    *
    * @param reference the name of the project
    * @return () or the appropriate rejection in the ''F'' context
    */
  def validateUnlocked(reference: ProjectReference): F[Unit] =
    resources.validateUnlocked(reference.toPersId)

  private implicit class IdRefSyntax(reference: ProjectReference) {
    lazy val toPersId: String = {
      //val idRef: IdRef = refToResolvable.apply(reference)
      val idRef: IdRef = idRes(reference)
      s"${idRef.namespace.host.hashCode.abs.toString.take(5)}-${reference.value}"
    }
  }

  private implicit def toProject(resource: F[Option[Resource[ProjectReference]]]): F[Option[Project]] =
    resource.flatMap {
      case Some(Resource(id, rev, value, deprecated)) =>
        value.as[ProjectValue] match {
          case Right(v)  => F.pure(Some(Project(id, rev, v, value, deprecated)))
          case Left(err) =>
            // $COVERAGE-OFF$
            logger.error(s"Could not convert json value '$value' to Value", err)
            F.raiseError(Unexpected(s"Could not convert json value '$value' to Value"))
          // $COVERAGE-ON$
        }
      case None => F.pure(None)
    }
}

object Projects {

  private[projects] implicit val logger: Logger = Logger[this.type]

  /**
    * Constructs a new ''Projects'' instance that bundles operations that can be performed against projects using the
    * underlying persistence abstraction.
    *
    * @param agg    the aggregate definition
    * @param F      a MonadError typeclass instance for ''F[_]''
    * @param clock  the clock used to issue instants
    * @param config the project specific settings
    * @tparam F the monadic effect type
    */
  final def apply[F[_]](
      agg: Agg[F])(implicit F: MonadError[F, Throwable], clock: Clock, config: ProjectsConfig): Projects[F] =
    new Projects(new Resources[F, ProjectReference](agg) {})

}

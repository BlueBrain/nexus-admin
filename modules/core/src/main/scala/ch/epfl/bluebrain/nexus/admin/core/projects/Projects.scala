package ch.epfl.bluebrain.nexus.admin.core.projects

import cats.MonadError
import cats.syntax.flatMap._
import ch.epfl.bluebrain.nexus.admin.core.CallerCtx
import ch.epfl.bluebrain.nexus.admin.core.Fault.Unexpected
import ch.epfl.bluebrain.nexus.admin.core.projects.Project.Value
import ch.epfl.bluebrain.nexus.admin.core.projects.Projects._
import ch.epfl.bluebrain.nexus.admin.core.resources.Resources.Agg
import ch.epfl.bluebrain.nexus.admin.core.resources.{Resource, Resources}
import ch.epfl.bluebrain.nexus.admin.core.types.ProjectReferenceOps._
import ch.epfl.bluebrain.nexus.admin.core.types.Ref._
import ch.epfl.bluebrain.nexus.admin.core.types.RefVersioned
import ch.epfl.bluebrain.nexus.admin.refined.permissions._
import ch.epfl.bluebrain.nexus.admin.refined.project._
import io.circe.Json
import io.circe.syntax._
import journal.Logger

class Projects[F[_]](resources: Resources[F, ProjectReference])(implicit F: MonadError[F, Throwable]) {

  private val tags = Set("project")

  def create(reference: ProjectReference, value: Value)(
      implicit ctx: CallerCtx,
      validatePerms: F[HasOwnProjects]): F[RefVersioned[ProjectReference]] =
    resources.create(reference, value.asJson, tags)

  def update(reference: ProjectReference, rev: Long, value: Value)(
      implicit ctx: CallerCtx,
      validatePerms: F[HasWriteProjects]): F[RefVersioned[ProjectReference]] =
    resources.update(reference, rev, value.asJson, tags)

  def deprecate(reference: ProjectReference, rev: Long)(
      implicit ctx: CallerCtx,
      validatePerms: F[HasWriteProjects]): F[RefVersioned[ProjectReference]] =
    resources.deprecate(reference, rev, tags)

  def fetch(reference: ProjectReference): F[Option[Project]] =
    resources.fetch(reference)

  def fetch(reference: ProjectReference, rev: Long): F[Option[Project]] =
    resources.fetch(reference, rev)

  def validateUnlocked(id: ProjectReference): F[Unit] =
    resources.validateUnlocked(id)

  private implicit def toProject(resource: F[Option[Resource[ProjectReference]]]): F[Option[Project]] =
    resource.flatMap {
      case Some(Resource(id, rev, value, deprecated)) =>
        value.as[Value] match {
          case Right(value) => F.pure(Some(Project(id, rev, value, deprecated)))
          case Left(err)    =>
            // $COVERAGE-OFF$
            logger.error(s"Could not convert json value '$value' to Value", err)
            F.raiseError(Unexpected(s"Could not convert json value '$value' to Value"))
          // $COVERAGE-ON$
        }
      case None => F.pure(None)
    }
}

object Projects {

  private[projects] implicit val logger = Logger[this.type]

  final def apply[F[_]](agg: Agg[F])(implicit F: MonadError[F, Throwable]): Projects[F] = {
    val resources = new Resources[F, ProjectReference](agg) {
      override def validate(id: ProjectReference, value: Json): F[Unit] = F.pure(())
    }
    new Projects(resources)
  }

}

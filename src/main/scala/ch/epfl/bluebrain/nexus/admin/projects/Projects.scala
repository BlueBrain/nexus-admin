package ch.epfl.bluebrain.nexus.admin.projects

import java.time.Clock
import java.util.UUID

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import cats.Monad
import cats.effect.ConcurrentEffect
import cats.syntax.functor._
import ch.epfl.bluebrain.nexus.admin.config.AppConfig
import ch.epfl.bluebrain.nexus.admin.config.AppConfig.HttpConfig
import ch.epfl.bluebrain.nexus.admin.index.Index
import ch.epfl.bluebrain.nexus.admin.projects.ProjectCommand._
import ch.epfl.bluebrain.nexus.admin.projects.ProjectRejection._
import ch.epfl.bluebrain.nexus.admin.projects.ProjectState._
import ch.epfl.bluebrain.nexus.commons.types.Meta
import ch.epfl.bluebrain.nexus.commons.types.identity.Identity
import ch.epfl.bluebrain.nexus.sourcing.Aggregate
import ch.epfl.bluebrain.nexus.sourcing.akka._

class Projects[F[_]](agg: Agg[F], index: Index)(implicit F: Monad[F], http: HttpConfig, clock: Clock) {

  /**
    * Creates a project.
    *
    * @param label       the project label (segment)
    * @param description an optional description
    * @param caller      an implicitly available caller
    * @return project the created project resource metadata if the operation was successful, a rejection otherwise
    */
  def create(label: ProjectLabel, description: Option[String])(implicit caller: Identity): F[ProjectMetaOrRejection] =
    index.getOrganization(label.organization) match {
      case Some(org) =>
        index.getProject(label) match {
          case None =>
            val projectId = UUID.randomUUID
            val command   = CreateProject(projectId, org.id, label, description, Meta(caller, clock.instant))
            eval(projectId.toString, command)
          case Some(_) => F.pure(Left(ProjectAlreadyExists))
        }
      case None => F.pure(Left(OrganizationDoesNotExist))
    }

  /**
    * Updates a project.
    *
    * @param label       the project label (segment)
    * @param description an optional description
    * @param caller      an implicitly available caller
    * @return the updated project resource metadata if the operation was successful, a rejection otherwise
    */
  def update(label: ProjectLabel, description: Option[String])(implicit caller: Identity): F[ProjectMetaOrRejection] =
    index.getProject(label) match {
      case Some(project) =>
        if (project.deprecated) F.pure(Left(ProjectIsDeprecated))
        else
          eval(project.id.toString,
               UpdateProject(project.id, project.label, description, project.rev, Meta(caller, clock.instant)))
      case None => F.pure(Left(ProjectDoesNotExists))
    }

  /**
    * Deprecates a project.
    *
    * @param label   the project label (segment)
    * @param rev     the project revision
    * @param caller  an implicitly available caller
    * @return the deprecated project resource metadata if the operation was successful, a rejection otherwise
    */
  def deprecate(label: ProjectLabel, rev: Long)(implicit caller: Identity): F[ProjectMetaOrRejection] =
    index.getProject(label) match {
      case Some(project) =>
        if (project.deprecated) F.pure(Left(ProjectIsDeprecated))
        else eval(project.id.toString, DeprecateProject(project.id, rev, Meta(caller, clock.instant)))
      case None => F.pure(Left(ProjectDoesNotExists))
    }

  /**
    * Fetches a project from the aggregate.
    *
    * @param id the project permanent identifier
    * @return Some(project) if found, None otherwise
    */
  def fetch(id: UUID): F[ProjectResourceOrRejection] = agg.currentState(id.toString).map {
    case c: Current => Right(c.toResource)
    case Initial    => Left(ProjectDoesNotExists)
  }

  /**
    * Fetches a specific revision of a project from the aggregate.
    *
    * @param id the project permanent identifier
    * @param rev the project revision
    * @return Some(project) if found, None otherwise
    */
  def fetch(id: UUID, rev: Long): F[ProjectResourceOrRejection] = {
    agg
      .foldLeft[ProjectState](id.toString, Initial) {
        case (state: Current, _) if state.rev == rev => state
        case (state, event)                          => ProjectState.next(state, event)
      }
      .map {
        case c: Current if c.rev == rev => Right(c.toResource)
        case _: Current                 => Left(IncorrectRevisionProvided)
        case Initial                    => Left(ProjectDoesNotExists)
      }
  }

  private def eval(id: String, command: ProjectCommand): F[ProjectMetaOrRejection] =
    agg
      .evaluateS(id, command)
      .map {
        case Right(c: Current) => Right(c.toResourceMetaData)
        case Left(rejection)   => Left(rejection)
        case Right(Initial)    => Left(UnexpectedState)
      }
}

object Projects {

  /**
    * Constructs a [[ch.epfl.bluebrain.nexus.admin.projects.Projects]] operation bundle.
    *
    * @param index           the project and organization label index
    * @param appConfig       the application configuration
    * @param sourcingConfig  the sourcing configuration
    * @tparam F              a [[cats.effect.ConcurrentEffect]] instance
    * @return the operations bundle in an ''F'' context.
    */
  def apply[F[_]: ConcurrentEffect](index: Index, appConfig: AppConfig, sourcingConfig: AkkaSourcingConfig)(
      implicit as: ActorSystem,
      mt: ActorMaterializer): F[Projects[F]] = {
    implicit val http: HttpConfig = appConfig.http
    implicit val clock: Clock     = Clock.systemUTC
    val aggF: F[Aggregate[F, String, ProjectEvent, ProjectState, ProjectCommand, ProjectRejection]] =
      AkkaAggregate.shardedF(
        "projects",
        ProjectState.Initial,
        ProjectState.next,
        ProjectState.Eval.apply[F],
        PassivationStrategy.immediately,
        RetryStrategy.never,
        sourcingConfig,
        appConfig.cluster.shards
      )
    aggF.map(agg => new Projects(agg, index))
  }
}

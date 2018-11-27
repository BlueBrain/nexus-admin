package ch.epfl.bluebrain.nexus.admin.projects

import java.time.Clock
import java.util.UUID

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import cats.MonadError
import cats.effect.ConcurrentEffect
import cats.syntax.flatMap._
import cats.syntax.functor._
import ch.epfl.bluebrain.nexus.admin.CommonRejection.UnexpectedState
import ch.epfl.bluebrain.nexus.admin.config.AppConfig
import ch.epfl.bluebrain.nexus.admin.config.AppConfig.HttpConfig
import ch.epfl.bluebrain.nexus.admin.index.Index
import ch.epfl.bluebrain.nexus.admin.projects.ProjectCommand._
import ch.epfl.bluebrain.nexus.admin.projects.ProjectRejection._
import ch.epfl.bluebrain.nexus.admin.projects.ProjectState._
import ch.epfl.bluebrain.nexus.admin.types.ResourceF
import ch.epfl.bluebrain.nexus.commons.types.identity.Identity
import ch.epfl.bluebrain.nexus.sourcing.Aggregate
import ch.epfl.bluebrain.nexus.sourcing.akka._

/**
  * The projects operations bundle.
  *
  * @param agg   an aggregate instance for projects
  * @param index the project and organization labels index
  * @tparam F    the effect type
  */
class Projects[F[_]](agg: Agg[F], index: Index)(implicit F: MonadError[F, Throwable], http: HttpConfig, clock: Clock) {

  /**
    * Creates a project.
    *
    * @param project the project
    * @param caller  an implicitly available caller
    * @return project the created project resource metadata if the operation was successful, a rejection otherwise
    */
  def create(project: Project)(implicit caller: Identity): F[ProjectMetaOrRejection] =
    index.getOrganization(project.organization) match {
      case Some(org) =>
        index.getProject(project.organization, project.label) match {
          case None =>
            val projectId = UUID.randomUUID
            val command   = CreateProject(projectId, org.uuid, project.label, project.description, clock.instant, caller)
            eval(projectId, command)
          case Some(_) => F.pure(Left(ProjectAlreadyExists))
        }
      case None => F.pure(Left(OrganizationDoesNotExist))
    }

  /**
    * Updates a project.
    *
    * @param project the project
    * @param caller  an implicitly available caller
    * @return the updated project resource metadata if the operation was successful, a rejection otherwise
    */
  def update(project: Project)(implicit caller: Identity): F[ProjectMetaOrRejection] =
    index.getProject(project.organization, project.label) match {
      case Some(resource) =>
        if (resource.deprecated) F.pure(Left(ProjectIsDeprecated))
        else
          eval(resource.uuid,
               UpdateProject(resource.uuid, project.label, project.description, resource.rev, clock.instant, caller))
      case None => F.pure(Left(ProjectDoesNotExists))
    }

  /**
    * Deprecates a project.
    *
    * @param project the project
    * @param rev     the project revision
    * @param caller  an implicitly available caller
    * @return the deprecated project resource metadata if the operation was successful, a rejection otherwise
    */
  def deprecate(project: Project, rev: Long)(implicit caller: Identity): F[ProjectMetaOrRejection] =
    index.getProject(project.organization, project.label) match {
      case Some(resource) =>
        if (resource.deprecated) F.pure(Left(ProjectIsDeprecated))
        else eval(resource.uuid, DeprecateProject(resource.uuid, rev, clock.instant, caller))
      case None => F.pure(Left(ProjectDoesNotExists))
    }

  /**
    * Fetches a project from the aggregate.
    *
    * @param uuid the project permanent identifier
    * @return Some(project) if found, None otherwise
    */
  def fetch(uuid: UUID): F[Option[ResourceF[Project]]] = agg.currentState(uuid.toString).map {
    case c: Current => toResource(c).toOption
    case Initial    => None
  }

  /**
    * Fetches a specific revision of a project from the aggregate.
    *
    * @param id the project permanent identifier
    * @param rev the project revision
    * @return the project if found, a rejection otherwise
    */
  def fetch(id: UUID, rev: Long): F[ProjectResourceOrRejection] = {
    agg
      .foldLeft[ProjectState](id.toString, Initial) {
        case (state: Current, _) if state.rev == rev => state
        case (state, event)                          => ProjectState.next(state, event)
      }
      .map {
        case c: Current if c.rev == rev => toResource(c)
        case _: Current                 => Left(IncorrectRev(rev))
        case Initial                    => Left(ProjectDoesNotExists)
      }
  }

  private def eval(id: UUID, command: ProjectCommand): F[ProjectMetaOrRejection] =
    agg
      .evaluateS(id.toString, command)
      .flatMap {
        case Right(c: Current) => F.pure(toResourceMetaData(c))
        case Left(rejection)   => F.pure(Left(rejection))
        case Right(Initial)    => F.raiseError(UnexpectedState)
      }

  private def toResource(c: Current): ProjectResourceOrRejection = index.getOrganization(c.organization) match {
    case Some(org) =>
      val iri = http.projectsIri + org.value.label + c.label
      Right(
        ResourceF(iri,
                  c.id,
                  c.rev,
                  c.deprecated,
                  types,
                  c.instant,
                  c.subject,
                  c.instant,
                  c.subject,
                  Project(c.label, org.value.label, c.description)))
    case None =>
      Left(OrganizationDoesNotExist)
  }

  private def toResourceMetaData(c: Current): ProjectMetaOrRejection = toResource(c).map(_.discard)

}

object Projects {

  /**
    * Constructs a [[ch.epfl.bluebrain.nexus.admin.projects.Projects]] operations bundle.
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

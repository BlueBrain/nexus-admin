package ch.epfl.bluebrain.nexus.admin.index

import java.util.UUID

import akka.actor.{ActorRef, ActorSystem}
import cats.MonadError
import cats.effect.{IO, LiftIO}
import cats.implicits._
import ch.epfl.bluebrain.nexus.admin.config.AppConfig.{IndexingConfig, PersistenceConfig}
import ch.epfl.bluebrain.nexus.admin.config.Vocabulary.nxv
import ch.epfl.bluebrain.nexus.admin.exceptions.AdminError.UnexpectedState
import ch.epfl.bluebrain.nexus.admin.organizations.{Organization, Organizations}
import ch.epfl.bluebrain.nexus.admin.projects.ProjectEvent.ProjectCreated
import ch.epfl.bluebrain.nexus.admin.projects.{Project, ProjectEvent, Projects}
import ch.epfl.bluebrain.nexus.admin.types.ResourceF
import ch.epfl.bluebrain.nexus.service.indexer.persistence.OffsetStorage.Volatile
import ch.epfl.bluebrain.nexus.service.indexer.persistence.{IndexerConfig, SequentialTagIndexer}

import scala.concurrent.Future

/**
  * Projects indexer.
  *
  * @param projects       project operations
  * @param organizations  organization operations
  * @param index          projects and organizations index
  */
class ProjectsIndexer[F[_]](projects: Projects[F], organizations: Organizations[F], index: Index[F])(
    implicit F: MonadError[F, Throwable]) {

  /**
    * Index a sequence of project events.
    *
    * @param events the list of events of to index.
    * @return       F.unit on success, error on failure.
    */
  def index(events: List[ProjectEvent]): F[Unit] = events.map(indexEvent).sequence *> F.unit

  private def indexEvent(event: ProjectEvent): F[Unit] = event match {
    case ProjectCreated(id, organization, _, _, _, _, _) =>
      for {
        org     <- fetchOrgOrRaiseError(organization)
        _       <- index.updateOrganization(org)
        project <- fetchProjectOrRaiseError(id)
        _       <- index.updateProject(project)
      } yield ()
    case ev: ProjectEvent => fetchProjectOrRaiseError(ev.id).flatMap(index.updateProject) *> F.unit

  }

  private def fetchProjectOrRaiseError(projectId: UUID): F[ResourceF[Project]] = projects.fetch(projectId).flatMap {
    case Some(project) => F.pure(project)
    case None          => F.raiseError(UnexpectedState(s"Couldn't find project ${projectId.toString} while indexing projects"))
  }

  private def fetchOrgOrRaiseError(orgId: UUID): F[ResourceF[Organization]] = organizations.fetch(orgId).flatMap {
    case Some(org) => F.pure(org)
    case None      => F.raiseError(UnexpectedState(s"Couldn't find organization ${orgId.toString} while indexing projects"))
  }

}

object ProjectsIndexer {

  /**
    *  Starts the projects indexer.
    *
    * @param projects       project operations
    * @param organizations  organization operations
    * @param index          projects and organizations index
    * @return               ActorRef for stream coordinator
    */
  final def start(projects: Projects[IO], organizations: Organizations[IO], index: Index[IO])(
      implicit
      as: ActorSystem,
      persistence: PersistenceConfig,
      indexing: IndexingConfig,
      F: MonadError[IO, Throwable],
      L: LiftIO[Future]): ActorRef = {

    val indexer = new ProjectsIndexer[IO](projects, organizations, index)

    SequentialTagIndexer.start(
      IndexerConfig.builder
        .name("projects-indexer")
        .tag(s"type=${nxv.Project.value.show}")
        .plugin(persistence.queryJournalPlugin)
        .retry(indexing.retry.maxCount, indexing.retry.strategy)
        .batch(indexing.batch, indexing.batchTimeout)
        .offset(Volatile)
        .index[ProjectEvent](indexer.index(_).to[Future])
        .build)
  }
}

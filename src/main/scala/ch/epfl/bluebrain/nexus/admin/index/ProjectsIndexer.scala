package ch.epfl.bluebrain.nexus.admin.index

import java.util.UUID

import akka.actor.{ActorRef, ActorSystem}
import cats.MonadError
import cats.implicits._
import ch.epfl.bluebrain.nexus.admin.client.types.events.ProjectEvent
import ch.epfl.bluebrain.nexus.admin.client.types.events.ProjectEvent._
import ch.epfl.bluebrain.nexus.admin.config.AppConfig
import ch.epfl.bluebrain.nexus.admin.exceptions.AdminError.UnexpectedState
import ch.epfl.bluebrain.nexus.admin.organizations.{OrganizationResource, Organizations}
import ch.epfl.bluebrain.nexus.admin.persistence.TaggingAdapter
import ch.epfl.bluebrain.nexus.admin.projects.{ProjectResource, Projects}
import ch.epfl.bluebrain.nexus.service.indexer.persistence.OffsetStorage.Volatile
import ch.epfl.bluebrain.nexus.service.indexer.persistence.{IndexerConfig, SequentialTagIndexer}
import monix.eval.Task
import monix.execution.Scheduler

/**
  * Projects indexer.
  *
  * @param projects      project operations
  * @param organizations organization operations
  * @param index         projects index
  * @param indexOrg      organizations index
  */
class ProjectsIndexer[F[_]](projects: Projects[F],
                            organizations: Organizations[F],
                            index: ProjectCache[F],
                            indexOrg: OrganizationCache[F])(implicit F: MonadError[F, Throwable]) {

  /**
    * Index a sequence of project events.
    *
    * @param events the list of events of to index.
    * @return       F.unit on success, error on failure.
    */
  def index(events: List[ProjectEvent]): F[Unit] = events.map(indexEvent).sequence *> F.unit

  private def indexEvent(event: ProjectEvent): F[Unit] = event match {
    case ProjectCreated(id, orgUuid, _, _, _, _, _, _) =>
      for {
        org     <- fetchOrgOrRaiseError(orgUuid)
        _       <- indexOrg.replace(orgUuid, org)
        project <- fetchProjectOrRaiseError(id)
        _       <- index.replace(id, project)
      } yield ()
    case ev: ProjectEvent =>
      fetchProjectOrRaiseError(ev.id).flatMap(project => index.replace(ev.id, project)) *> F.unit

  }

  private def fetchProjectOrRaiseError(projectId: UUID): F[ProjectResource] = projects.fetch(projectId).flatMap {
    case Some(project) => F.pure(project)
    case None          => F.raiseError(UnexpectedState(s"Couldn't find project ${projectId.toString} while indexing projects"))
  }

  private def fetchOrgOrRaiseError(orgId: UUID): F[OrganizationResource] =
    organizations.fetch(orgId).flatMap {
      case Some(org) => F.pure(org)
      case None =>
        F.raiseError(UnexpectedState(s"Couldn't find organization ${orgId.toString} while indexing projects"))
    }

}

object ProjectsIndexer {

  /**
    *  Starts the projects indexer.
    *
    * @param projects       project operations
    * @param organizations  organization operations
    * @param index          projects index
    * @return               ActorRef for stream coordinator
    */
  final def start(projects: Projects[Task],
                  organizations: Organizations[Task],
                  index: ProjectCache[Task],
                  indexOrg: OrganizationCache[Task])(
      implicit
      as: ActorSystem,
      appConfig: AppConfig,
      s: Scheduler
  ): ActorRef = {

    val indexer = new ProjectsIndexer[Task](projects, organizations, index, indexOrg)

    SequentialTagIndexer.start(
      IndexerConfig.builder
        .name("projects-indexer")
        .tag(TaggingAdapter.ProjectTag)
        .plugin(appConfig.persistence.queryJournalPlugin)
        .retry(appConfig.indexing.retry.maxCount, appConfig.indexing.retry.strategy)
        .batch(appConfig.indexing.batch, appConfig.indexing.batchTimeout)
        .offset(Volatile)
        .index[ProjectEvent](indexer.index(_).runToFuture)
        .build)
  }
}

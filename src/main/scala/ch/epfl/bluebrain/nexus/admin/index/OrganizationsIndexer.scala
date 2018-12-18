package ch.epfl.bluebrain.nexus.admin.index

import java.util.UUID

import akka.actor.{ActorRef, ActorSystem}
import cats.MonadError
import cats.implicits._
import ch.epfl.bluebrain.nexus.admin.config.AppConfig
import ch.epfl.bluebrain.nexus.admin.exceptions.AdminError.UnexpectedState
import ch.epfl.bluebrain.nexus.admin.organizations._
import ch.epfl.bluebrain.nexus.admin.persistence.TaggingAdapter
import ch.epfl.bluebrain.nexus.admin.types.ResourceF
import ch.epfl.bluebrain.nexus.service.indexer.persistence.OffsetStorage.Volatile
import ch.epfl.bluebrain.nexus.service.indexer.persistence.{IndexerConfig, SequentialTagIndexer}
import monix.eval.Task
import monix.execution.Scheduler

/**
  * Organizations indexer.
  *
  * @param organizations  organization operations
  * @param index          projects and organizations index
  */
class OrganizationsIndexer[F[_]](organizations: Organizations[F], index: Index[F])(
    implicit F: MonadError[F, Throwable]) {

  /**
    * Index a sequence of organization events.
    *
    * @param events the list of events of to index.
    * @return       F.unit on success, error on failure.
    */
  def index(events: List[OrganizationEvent]): F[Unit] = events.map(indexEvent).sequence *> F.unit

  private def indexEvent(event: OrganizationEvent): F[Unit] =
    fetchOrgOrRaiseError(event.id).flatMap(org => index.updateOrganization(org)) *> F.unit

  private def fetchOrgOrRaiseError(orgId: UUID): F[ResourceF[Organization]] = organizations.fetch(orgId).flatMap {
    case Some(org) => F.pure(org)
    case None =>
      F.raiseError(UnexpectedState(s"Couldn't find organization ${orgId.toString} while indexing organizations"))
  }
}

object OrganizationsIndexer {

  /**
    *  Starts the organizations indexer.
    *
    * @param organizations  organization operations
    * @param index          projects and organizations index
    * @return               ActorRef for stream coordinator
    */
  final def start(organizations: Organizations[Task], index: Index[Task])(implicit
                                                                          as: ActorSystem,
                                                                          appConfig: AppConfig,
                                                                          s: Scheduler): ActorRef = {

    val indexer = new OrganizationsIndexer[Task](organizations, index)

    SequentialTagIndexer.start(
      IndexerConfig.builder
        .name("orgs-indexer")
        .tag(TaggingAdapter.OrganizationTag)
        .plugin(appConfig.persistence.queryJournalPlugin)
        .retry(appConfig.indexing.retry.maxCount, appConfig.indexing.retry.strategy)
        .batch(appConfig.indexing.batch, appConfig.indexing.batchTimeout)
        .offset(Volatile)
        .index[OrganizationEvent](indexer.index(_).runToFuture)
        .build)
  }

}

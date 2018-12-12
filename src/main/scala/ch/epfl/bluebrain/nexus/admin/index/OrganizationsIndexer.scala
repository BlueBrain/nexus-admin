package ch.epfl.bluebrain.nexus.admin.index

import java.util.UUID

import akka.actor.{ActorRef, ActorSystem}
import cats.MonadError
import cats.effect.{IO, LiftIO}
import cats.implicits._
import ch.epfl.bluebrain.nexus.admin.config.AppConfig.{IndexingConfig, PersistenceConfig}
import ch.epfl.bluebrain.nexus.admin.config.Vocabulary.nxv
import ch.epfl.bluebrain.nexus.admin.exceptions.AdminError.UnexpectedState
import ch.epfl.bluebrain.nexus.admin.organizations._
import ch.epfl.bluebrain.nexus.admin.types.ResourceF
import ch.epfl.bluebrain.nexus.service.indexer.persistence.OffsetStorage.Volatile
import ch.epfl.bluebrain.nexus.service.indexer.persistence.{IndexerConfig, SequentialTagIndexer}

import scala.concurrent.Future

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
  final def start(organizations: Organizations[IO], index: Index[IO])(implicit
                                                                      as: ActorSystem,
                                                                      persistence: PersistenceConfig,
                                                                      indexing: IndexingConfig,
                                                                      F: MonadError[IO, Throwable],
                                                                      L: LiftIO[Future]): ActorRef = {

    val indexer = new OrganizationsIndexer[IO](organizations, index)

    SequentialTagIndexer.start(
      IndexerConfig.builder
        .name("orgs-indexer")
        .tag(s"type=${nxv.Organization.value.show}")
        .plugin(persistence.queryJournalPlugin)
        .retry(indexing.retry.maxCount, indexing.retry.strategy)
        .batch(indexing.batch, indexing.batchTimeout)
        .offset(Volatile)
        .index[OrganizationEvent](indexer.index(_).to[Future])
        .build)
  }

}

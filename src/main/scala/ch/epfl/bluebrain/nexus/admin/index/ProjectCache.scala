package ch.epfl.bluebrain.nexus.admin.index

import java.util.UUID

import akka.actor.ActorSystem
import cats.Monad
import cats.effect.{Async, Timer}
import cats.implicits._
import ch.epfl.bluebrain.nexus.admin.config.Permissions._
import ch.epfl.bluebrain.nexus.admin.index.Cache._
import ch.epfl.bluebrain.nexus.admin.projects.{Project, ProjectResource}
import ch.epfl.bluebrain.nexus.commons.cache.{KeyValueStore, KeyValueStoreConfig}
import ch.epfl.bluebrain.nexus.commons.search.Pagination
import ch.epfl.bluebrain.nexus.commons.search.QueryResult.UnscoredQueryResult
import ch.epfl.bluebrain.nexus.commons.search.QueryResults.UnscoredQueryResults
import ch.epfl.bluebrain.nexus.iam.client.types.AccessControlLists

/**
  * The project cache backed by a KeyValueStore using akka Distributed Data
  *
  * @param store the underlying Distributed Data LWWMap store.
  * @tparam F the effect type ''F[_]''
  */
class ProjectCache[F[_]](store: KeyValueStore[F, UUID, ProjectResource])(implicit F: Monad[F])
    extends Cache[F, Project](store) {

  private implicit val ordering: Ordering[ProjectResource] = Ordering.by { proj: ProjectResource =>
    s"${proj.value.organizationLabel}/${proj.value.label}"
  }

  /**
    * Return the elements on the store within the ''pagination'' bounds which are accessible by the provided acls with the permission 'projects/read'.
    * The elements returned are filtered by the provided organization label.
    *
    * @param orgLabel the organization label
    * @param pagination the pagination
    */
  def list(orgLabel: String, pagination: Pagination)(
      implicit acls: AccessControlLists): F[UnscoredQueryResults[ProjectResource]] =
    store.values.map { values =>
      val filtered = values.filter { proj =>
        proj.value.organizationLabel == orgLabel && acls.exists(proj.value.organizationLabel,
                                                                proj.value.label,
                                                                projects.read)
      }
      val count  = filtered.size.toLong
      val result = filtered.toList.sorted.slice(pagination.from.toInt, (pagination.from + pagination.size).toInt)
      UnscoredQueryResults(count, result.map(UnscoredQueryResult(_)))
    }

  /**
    * Return the elements on the store within the ''pagination'' bounds which are accessible by the provided acls with the permission 'projects/read'.
    *
    * @param pagination the pagination
    */
  def list(pagination: Pagination)(implicit acls: AccessControlLists): F[UnscoredQueryResults[ProjectResource]] =
    store.values.map { values =>
      val filtered = values.filter { proj =>
        acls.exists(proj.value.organizationLabel, proj.value.label, projects.read)
      }
      val count  = filtered.size.toLong
      val result = filtered.toList.sorted.slice(pagination.from.toInt, (pagination.from + pagination.size).toInt)
      UnscoredQueryResults(count, result.map(UnscoredQueryResult(_)))
    }

  /**
    * Attempts to fetch the project resource with the provided organization and project labels
    *
    * @param org  the organization label
    * @param proj the project label
    */
  def getBy(org: String, proj: String): F[Option[ProjectResource]] =
    store.findValue(r => r.value.organizationLabel == org && r.value.label == proj)
}

object ProjectCache {

  /**
    * Creates a new project index.
    */
  def apply[F[_]: Timer](implicit as: ActorSystem, config: KeyValueStoreConfig, F: Async[F]): ProjectCache[F] = {
    val function: (Long, ProjectResource) => Long = { case (_, res) => res.rev }
    new ProjectCache(KeyValueStore.distributed("projects", function, mapError))(F)
  }
}

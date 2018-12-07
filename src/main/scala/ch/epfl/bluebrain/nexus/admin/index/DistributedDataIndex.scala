package ch.epfl.bluebrain.nexus.admin.index

import akka.actor.ActorSystem
import akka.cluster.Cluster
import akka.cluster.ddata.LWWRegister.Clock
import akka.cluster.ddata.Replicator._
import akka.cluster.ddata.{LWWMapKey, _}
import akka.pattern.ask
import akka.util.Timeout
import cats.effect.{Async, IO}
import cats.syntax.apply._
import cats.syntax.flatMap._
import ch.epfl.bluebrain.nexus.admin.exceptions.DistributedDataGetError
import ch.epfl.bluebrain.nexus.admin.organizations.Organization
import ch.epfl.bluebrain.nexus.admin.projects.Project
import ch.epfl.bluebrain.nexus.admin.types.ResourceF
import ch.epfl.bluebrain.nexus.commons.types.RetriableErr
import ch.epfl.bluebrain.nexus.commons.types.search.Pagination

import scala.concurrent.duration.FiniteDuration

/**
  * Index implementation based on Akka Distributed Data.
  *
  * @param askTimeout         actor ask timeout
  * @param consistencyTimeout distributed data the consistency timeout
  */
class DistributedDataIndex[F[_]](askTimeout: Timeout, consistencyTimeout: FiniteDuration)(implicit as: ActorSystem,
                                                                                          F: Async[F])
    extends Index[F] {

  private implicit val node: Cluster = Cluster(as)

  private implicit def orgClock[A]: Clock[ResourceF[A]] = { (_: Long, value: ResourceF[A]) =>
    value.rev
  }
  private implicit val timeout: Timeout = askTimeout

  private val replicator = DistributedData(as).replicator

  private val labelToProjectsKey      = LWWMapKey[String, ResourceF[Project]]("labels_to_projects")
  private val labelToOrganizationsKey = LWWMapKey[String, ResourceF[Organization]]("labels_to_orgs")

  private implicit val orgOrdering: Ordering[ResourceF[Organization]] = Ordering.by { org: ResourceF[Organization] =>
    org.value.label
  }

  private implicit val projectOrdering: Ordering[ResourceF[Project]] = Ordering.by { proj: ResourceF[Project] =>
    s"${proj.value.organization}/${proj.value.label}"
  }

  override def updateOrganization(organization: ResourceF[Organization]): F[Boolean] =
    updateUuidToResource(labelToOrganizationsKey, organization.value.label, organization) *> F.pure(true)

  override def updateProject(project: ResourceF[Project]): F[Boolean] = {
    updateUuidToResource(labelToProjectsKey, project.value.fullLabel, project) *> F.pure(true)
  }

  override def getOrganization(label: String): F[Option[ResourceF[Organization]]] =
    fetchFromMap(labelToOrganizationsKey, label)

  override def getProject(organization: String, project: String): F[Option[ResourceF[Project]]] =
    fetchFromMap(labelToProjectsKey, s"$organization/$project")

  override def listOrganizations(pagination: Pagination): F[List[ResourceF[Organization]]] =
    fetchListFromMap(labelToOrganizationsKey, pagination)

  override def listProjects(pagination: Pagination): F[List[ResourceF[Project]]] =
    fetchListFromMap(labelToProjectsKey, pagination)

  override def listProjects(organizationLabel: String, pagination: Pagination): F[List[ResourceF[Project]]] = {
    val get = Get(labelToProjectsKey, ReadLocal, None)
    IO.fromFuture(IO(replicator ? get)).to[F].flatMap {
      case g @ GetSuccess(`labelToProjectsKey`, _) =>
        F.pure(
          g.get(labelToProjectsKey)
            .entries
            .values
            .filter(_.value.organization == organizationLabel)
            .toList
            .sorted
            .slice(pagination.from.toInt, (pagination.from + pagination.size).toInt)
        )
      case NotFound(_, _)   => F.pure(List.empty)
      case f: GetFailure[_] => F.raiseError(DistributedDataGetError(f.key.id))
    }
  }

  private def fetchListFromMap[A](dDataKey: LWWMapKey[String, A], pagination: Pagination)(
      implicit ordering: Ordering[A]): F[List[A]] = {
    val get = Get(dDataKey, ReadLocal, None)
    IO.fromFuture(IO(replicator ? get)).to[F].flatMap {
      case g @ GetSuccess(`dDataKey`, _) =>
        F.pure(
          g.get(dDataKey)
            .entries
            .values
            .toList
            .sorted
            .slice(pagination.from.toInt, (pagination.from + pagination.size).toInt)
        )
      case NotFound(_, _)   => F.pure(List.empty)
      case f: GetFailure[_] => F.raiseError(DistributedDataGetError(f.key.id))
    }
  }

  private def updateUuidToResource[A: Clock](dDataKey: LWWMapKey[String, A], mapKey: String, value: A): F[Unit] = {
    val msg =
      Update(dDataKey, LWWMap.empty[String, A], WriteAll(consistencyTimeout)) {
        _.put(mapKey, value)
      }
    update(msg, s"update $mapKey -> resource")
  }

  private def fetchFromMap[A](dDataKey: LWWMapKey[String, A], mapKey: String): F[Option[A]] = {
    val get = Get(dDataKey, ReadLocal, None)
    IO.fromFuture(IO(replicator ? get)).to[F].flatMap {
      case g @ GetSuccess(`dDataKey`, _) =>
        F.pure(g.get(dDataKey).get(mapKey))
      case NotFound(_, _)   => F.pure(None)
      case f: GetFailure[_] => F.raiseError(DistributedDataGetError(f.key.id))
    }
  }

  private def update(update: Update[_], action: String): F[Unit] = {
    IO.fromFuture(IO(replicator ? update)).to[F].flatMap {
      case UpdateSuccess(_, _) =>
        F.unit
      case UpdateTimeout(LWWRegisterKey(_), _) =>
        F.raiseError(new RetriableErr(s"Distributed cache update timed out while performing action '$action'"))
      case ModifyFailure(LWWRegisterKey(_), _, cause, _) =>
        F.raiseError(cause)
      case StoreFailure(LWWRegisterKey(_), _) =>
        F.raiseError(new RetriableErr(s"Failed to replicate the update for: '$action'"))
    }
  }
}

object DistributedDataIndex {

  /**
    * Create an instance of [[DistributedDataIndex]]
    *
    * @param askTimeout         actor ask timeout
    * @param consistencyTimeout distributed data the consistency timeout
    */
  def apply[F[_]](askTimeout: Timeout, consistencyTimeout: FiniteDuration)(implicit as: ActorSystem,
                                                                           F: Async[F]): DistributedDataIndex[F] =
    new DistributedDataIndex(askTimeout, consistencyTimeout)
}

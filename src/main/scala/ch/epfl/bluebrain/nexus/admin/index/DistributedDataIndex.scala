package ch.epfl.bluebrain.nexus.admin.index

import java.util.UUID

import akka.actor.ActorSystem
import akka.cluster.Cluster
import akka.cluster.ddata.LWWRegister.Clock
import akka.cluster.ddata.Replicator._
import akka.cluster.ddata.{LWWMapKey, _}
import akka.pattern.ask
import akka.util.Timeout
import cats.effect.{Async, IO}
import cats.syntax.flatMap._
import cats.syntax.functor._
import ch.epfl.bluebrain.nexus.admin.exceptions.UnexpectedState
import ch.epfl.bluebrain.nexus.admin.organizations.Organization
import ch.epfl.bluebrain.nexus.admin.projects.Project
import ch.epfl.bluebrain.nexus.admin.types.ResourceF
import ch.epfl.bluebrain.nexus.commons.types.RetriableErr
import ch.epfl.bluebrain.nexus.commons.types.search.Pagination

import scala.collection.SortedSet
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
  private val organizationsListKey    = LWWRegisterKey[RevisionedValue[SortedSet[ResourceF[Organization]]]]("orgs_list")
  private val projectsListKey         = LWWRegisterKey[RevisionedValue[SortedSet[ResourceF[Project]]]]("projects_list")

  private def projectsOrgListKey(org: UUID): LWWRegisterKey[RevisionedValue[SortedSet[ResourceF[Project]]]] =
    LWWRegisterKey[RevisionedValue[SortedSet[ResourceF[Project]]]](s"${org.toString}_projects_list")

  private implicit val orgOrdering: Ordering[ResourceF[Organization]] = Ordering.by { org: ResourceF[Organization] =>
    org.value.label
  }

  private implicit val projectOrdering: Ordering[ResourceF[Project]] = Ordering.by { proj: ResourceF[Project] =>
    s"${proj.value.organization}/${proj.value.label}"
  }

  override def updateOrganization(organization: ResourceF[Organization]): F[Boolean] =
    for {
      _ <- updateUuidToResource(labelToOrganizationsKey, organization.value.label, organization)
      _ <- updateList(organizationsListKey, organization)
    } yield true

  override def updateProject(project: ResourceF[Project]): F[Boolean] = {

    def unwrapUuid(orgOpt: Option[ResourceF[Organization]]) = orgOpt match {
      case None      => F.raiseError(UnexpectedState(project.uuid.toString))
      case Some(org) => F.pure(org.uuid)
    }

    for {
      _    <- updateUuidToResource(labelToProjectsKey, project.value.fullLabel, project)
      _    <- updateList(projectsListKey, project)
      org  <- getOrganization(project.value.organization)
      uuid <- unwrapUuid(org)
      _    <- updateList(projectsOrgListKey(uuid), project)
    } yield true
  }

  override def getOrganization(label: String): F[Option[ResourceF[Organization]]] =
    fetchFromMap(labelToOrganizationsKey, label)

  override def getProject(organization: String, project: String): F[Option[ResourceF[Project]]] =
    fetchFromMap(labelToProjectsKey, s"$organization/$project")

  override def listOrganizations(pagination: Pagination): F[List[ResourceF[Organization]]] =
    fetchList(organizationsListKey, pagination)

  override def listProjects(pagination: Pagination): F[List[ResourceF[Project]]] =
    fetchList(projectsListKey, pagination)

  override def listProjects(organizationLabel: String, pagination: Pagination): F[List[ResourceF[Project]]] = {
    getOrganization(organizationLabel).flatMap {
      case None => F.pure(List.empty)
      case Some(org) =>
        fetchList(projectsOrgListKey(org.uuid), pagination)
    }
  }

  private def fetchList[A](dDataKey: LWWRegisterKey[RevisionedValue[SortedSet[ResourceF[A]]]],
                           pagination: Pagination): F[List[ResourceF[A]]] = {
    val get = Get(dDataKey, ReadMajority(consistencyTimeout), None)
    IO.fromFuture(IO(replicator ? get)).to[F].flatMap {
      case g @ GetSuccess(`dDataKey`, _) =>
        F.pure(
          g.get(dDataKey)
            .value
            .value
            .slice(pagination.from.toInt, (pagination.from + pagination.size).toInt)
            .toList)
      case NotFound(_, _) => F.pure(List.empty)
    }
  }

  private def updateUuidToResource[A: Clock](dDataKey: LWWMapKey[String, A], mapKey: String, value: A): F[Unit] = {
    val msg =
      Update(dDataKey, LWWMap.empty[String, A], WriteMajority(consistencyTimeout)) {
        _.put(mapKey, value)
      }
    update(msg, s"update $mapKey -> resource")
  }

  private def updateList[A](dDataKey: LWWRegisterKey[RevisionedValue[SortedSet[ResourceF[A]]]], value: ResourceF[A])(
      implicit ordering: Ordering[ResourceF[A]]): F[Unit] = {
    val msg = Update(
      dDataKey,
      LWWRegister(RevisionedValue.apply[SortedSet[ResourceF[A]]](0, SortedSet.empty[ResourceF[A]])),
      WriteMajority(consistencyTimeout)
    )(updateWithIncrement(_, value))
    update(msg, s"update ${value.uuid.toString} in ${dDataKey._id}")
  }

  private def fetchFromMap[A](dDataKey: LWWMapKey[String, A], mapKey: String): F[Option[A]] = {
    val get = Get(dDataKey, ReadMajority(consistencyTimeout), None)
    IO.fromFuture(IO(replicator ? get)).to[F].flatMap {
      case g @ GetSuccess(`dDataKey`, _) =>
        F.pure(g.get(dDataKey).get(mapKey))
      case NotFound(_, _) => F.pure(None)
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
  private def updateWithIncrement[A](currentState: LWWRegister[RevisionedValue[SortedSet[ResourceF[A]]]],
                                     value: ResourceF[A]): LWWRegister[RevisionedValue[SortedSet[ResourceF[A]]]] = {

    val currentRevision = currentState.value.rev
    val current         = currentState.value.value

    current.find(_.id == value.id) match {
      case Some(r) if r.rev >= value.rev => currentState
      case Some(r) =>
        val updated  = current - r + value
        val newValue = RevisionedValue(currentRevision + 1, updated)
        currentState.withValue(newValue)
      case None =>
        val updated  = current + value
        val newValue = RevisionedValue(currentRevision + 1, updated)
        currentState.withValue(newValue)
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

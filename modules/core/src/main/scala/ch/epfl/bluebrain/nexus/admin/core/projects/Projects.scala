package ch.epfl.bluebrain.nexus.admin.core.projects

import java.time.Clock
import java.util.UUID

import cats.MonadError
import cats.syntax.all._
import ch.epfl.bluebrain.nexus.admin.core.config.AppConfig._
import ch.epfl.bluebrain.nexus.admin.core.organizations.Organizations
import ch.epfl.bluebrain.nexus.admin.core.persistence.PersistenceId
import ch.epfl.bluebrain.nexus.admin.core.persistence.PersistenceId._
import ch.epfl.bluebrain.nexus.admin.core.resources.ResourceCommand.CreateResource
import ch.epfl.bluebrain.nexus.admin.core.resources.Resources.Agg
import ch.epfl.bluebrain.nexus.admin.core.resources._
import ch.epfl.bluebrain.nexus.admin.core.syntax.caller._
import ch.epfl.bluebrain.nexus.admin.core.types.Ref._
import ch.epfl.bluebrain.nexus.admin.core.types.RefVersioned
import ch.epfl.bluebrain.nexus.admin.ld.Const._
import ch.epfl.bluebrain.nexus.admin.ld.JsonLD._
import ch.epfl.bluebrain.nexus.admin.ld.{IdRef, JsonLD}
import ch.epfl.bluebrain.nexus.admin.refined.project._
import ch.epfl.bluebrain.nexus.commons.sparql.client.SparqlClient
import ch.epfl.bluebrain.nexus.iam.client.Caller
import ch.epfl.bluebrain.nexus.rdf.Graph
import ch.epfl.bluebrain.nexus.service.http.Path._
import ch.epfl.bluebrain.nexus.service.http.UriOps._
import io.circe.Json
import journal.Logger

/**
  * Bundles operations that can be performed against a project using the underlying persistence abstraction.
  *
  * @param organizations  organizations operation bundle
  * @param agg            aggregate for projects
  * @param F         a MonadError typeclass instance for ''F[_]''
  * @tparam F the monadic effect type
  */
class Projects[F[_]](organizations: Organizations[F], agg: Agg[F], sparqlClient: SparqlClient[F])(
    implicit
    F: MonadError[F, Throwable],
    logger: Logger,
    clock: Clock,
    config: ProjectsConfig,
    persConfig: PersistenceConfig,
    http: HttpConfig,
    persistenceId: PersistenceId[ProjectReference])
    extends Resources[F, ProjectReference](agg, sparqlClient) {

  override def validateUnlocked(id: ProjectReference): F[Unit] =
    for {
      _ <- super.validateUnlocked(id)
      _ <- organizations.validateUnlocked(id.organizationReference)
    } yield ()

  override def validate(id: ProjectReference, value: JsonLD): F[Unit] =
    for {
      _ <- super.validate(id, value)
      _ <- organizations.validateUnlocked(id.organizationReference)
    } yield ()

  override def label(id: ProjectReference): String = id.projectLabel.value

  private def addBaseIfNotPresent(id: ProjectReference, value: Json): Json = {
    lazy val baseUri = http.apiUri.append(id.organizationReference.value / id.projectLabel.value).toString() + "/"
    value.hcursor.get[String](nxv.base.reference.value) match {
      case Left(_) => value deepMerge Json.obj(nxv.base.reference.value -> Json.fromString(baseUri))
      case _       => value
    }
  }

  override def create(id: ProjectReference, value: Json)(implicit caller: Caller): F[RefVersioned[ProjectReference]] = {
    val finalJson = addBaseIfNotPresent(id, value)
    for {
      _       <- validate(id, finalJson)
      orgUuid <- organizations.fetch(id.organizationReference).map(_.map(_.uuid))
      r <- evaluate(CreateResource(id,
                                   label(id),
                                   UUID.randomUUID.toString,
                                   orgUuid,
                                   caller.meta,
                                   tags + id.persistenceId,
                                   finalJson),
                    id.persistenceId,
                    s"Create project '$id'")
    } yield RefVersioned(id, r.rev)
  }

  override def update(id: ProjectReference, rev: Long, value: Json)(implicit caller: Caller) =
    super.update(id, rev, addBaseIfNotPresent(id, value))

  override val tags: Set[String] = Set("project", persConfig.defaultTag)

  override val resourceType: IdRef = nxv.Project

  override val resourceSchema: Graph = projectSchema
}

object Projects {

  private[projects] implicit val logger: Logger = Logger[this.type]

  implicit val projectReferencePersistenceId = new PersistenceId[ProjectReference] {
    override def persistenceId(id: ProjectReference): String = id.show
  }

  /**
    * Constructs a new ''Projects'' instance that bundles operations that can be performed against projects using the
    * underlying persistence abstraction.
    *
    * @param organizations organizations operation bundle
    * @param agg           the aggregate definition
    * @param F             a MonadError typeclass instance for ''F[_]''
    * @param config        the project specific settings
    * @param persConfig    the persistence specific settings
    * @tparam F the monadic effect type
    */
  final def apply[F[_]](organizations: Organizations[F], agg: Agg[F], sparqlClient: SparqlClient[F])(
      implicit F: MonadError[F, Throwable],
      config: ProjectsConfig,
      persConfig: PersistenceConfig,
      http: HttpConfig,
  ): Projects[F] = {
    implicit val logger: Logger = Logger[this.type]
    new Projects[F](organizations, agg, sparqlClient)
  }
}

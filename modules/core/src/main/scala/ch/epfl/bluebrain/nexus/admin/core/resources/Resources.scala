package ch.epfl.bluebrain.nexus.admin.core.resources

import java.time.Clock
import java.util.UUID

import cats.MonadError
import cats.implicits._
import ch.epfl.bluebrain.nexus.admin.core.Fault.{CommandRejected, Unexpected}
import ch.epfl.bluebrain.nexus.admin.core.persistence.PersistenceId
import ch.epfl.bluebrain.nexus.admin.core.persistence.PersistenceId._
import ch.epfl.bluebrain.nexus.admin.core.resources.ResourceCommand._
import ch.epfl.bluebrain.nexus.admin.core.resources.ResourceRejection._
import ch.epfl.bluebrain.nexus.admin.core.resources.ResourceState._
import ch.epfl.bluebrain.nexus.admin.core.resources.Resources.Agg
import ch.epfl.bluebrain.nexus.admin.core.syntax.caller._
import ch.epfl.bluebrain.nexus.admin.core.types.Ref._
import ch.epfl.bluebrain.nexus.admin.core.types.RefVersioned
import ch.epfl.bluebrain.nexus.admin.ld.Const.{nxv, rdf, resourceContext}
import ch.epfl.bluebrain.nexus.admin.ld.{IdRef, IdResolvable, JsonLD}
import ch.epfl.bluebrain.nexus.admin.query.QueryPayload
import ch.epfl.bluebrain.nexus.admin.query.QueryResultsOps._
import ch.epfl.bluebrain.nexus.admin.query.builder.{FilteredQuery, TypeFilterExpr}
import ch.epfl.bluebrain.nexus.admin.refined.ld.Id
import ch.epfl.bluebrain.nexus.admin.refined.ld.Uri._
import ch.epfl.bluebrain.nexus.admin.refined.permissions._
import ch.epfl.bluebrain.nexus.admin.refined.project.ProjectReference
import ch.epfl.bluebrain.nexus.commons.http.HttpClient
import ch.epfl.bluebrain.nexus.commons.shacl.validator.ShaclValidatorErr.{CouldNotFindImports, IllegalImportDefinition}
import ch.epfl.bluebrain.nexus.commons.shacl.validator.{ShaclSchema, ShaclValidator}
import ch.epfl.bluebrain.nexus.commons.sparql.client.SparqlClient
import ch.epfl.bluebrain.nexus.commons.types.search.{Pagination, QueryResults}
import ch.epfl.bluebrain.nexus.iam.client.Caller
import ch.epfl.bluebrain.nexus.sourcing.Aggregate
import io.circe.Json
import journal.Logger

/**
  * Bundles operations that can be performed against a resource using the underlying persistence abstraction.
  *
  * @param agg    the aggregate definition
  * @param F      a MonadError typeclass instance for ''F[_]''
  * @param logger the logger
  * @param clock  the clock used to issue instants
  * @tparam F the monadic effect type
  * @tparam A the generic type of the id's ''reference''
  */
@SuppressWarnings(Array("UnusedMethodParameter"))
abstract class Resources[F[_], A: IdResolvable: PersistenceId: TypeFilterExpr](agg: Agg[F],
                                                                               sparqlClient: SparqlClient[F])(
    implicit
    F: MonadError[F, Throwable],
    validator: ShaclValidator[F],
    logger: Logger,
    clock: Clock) {

  def resourceType: IdRef

  def resourceSchema: Json

  /**
    * Certain validation to perform on the payload.
    *
    * @param id    the identifier of the resource
    * @param value the json payload of the resource
    * @return () or the appropriate rejection in the ''F'' context
    */
  def validate(id: A, value: JsonLD): F[Unit] = {
    value.appendContext(resourceContext).add(rdf.tpe, resourceType).add(nxv.label, id.value).apply() match {
      case Some(merged) =>
        validator(ShaclSchema(resourceSchema), merged)
          .flatMap { report =>
            if (report.conforms) F.pure(())
            else F.raiseError[Unit](CommandRejected(ShapeConstraintViolations(report.result.map(_.reason))))
          }
          .recoverWith {
            case CouldNotFindImports(missing)     => F.raiseError(CommandRejected(MissingImportsViolation(missing)))
            case IllegalImportDefinition(missing) => F.raiseError(CommandRejected(IllegalImportsViolation(missing)))
          }
      case None => F.raiseError(Unexpected(s"Could not add @type to the payload '${value.json}'"))
    }
  }

  def tags: Set[String]

  /**
    * Asserts the resource exists and it allows modifications on children resources.
    *
    * @param id  the unique id of the resource
    * @return () or the appropriate rejection in the ''F'' context
    */
  def validateUnlocked(id: A): F[Unit] =
    agg.currentState(id.persistenceId) flatMap {
      case Initial                    => F.raiseError(CommandRejected(ParentResourceDoesNotExist))
      case c: Current if c.deprecated => F.raiseError(CommandRejected(ResourceIsDeprecated))
      case _                          => F.pure(())
    }

  /**
    * Attempts to create a new resource instance.
    *
    * @param id     the identifier of the resource
    * @param value  the json payload of the resource
    * @return a [[RefVersioned]] instance wrapped in the abstract ''F[_]'' type
    *         if successful, or a [[ch.epfl.bluebrain.nexus.admin.core.Fault]] wrapped within ''F[_]'' otherwise
    */
  def create(id: A, value: Json)(implicit caller: Caller): F[RefVersioned[A]] =
    for {
      _ <- validate(id, value)
      r <- evaluate(CreateResource(id, UUID.randomUUID.toString, None, caller.meta, tags + id.persistenceId, value),
                    id.persistenceId,
                    s"Create res '$id'")
    } yield RefVersioned(id, r.rev)

  /**
    * Attempts to update an existing resource instance with a new json payload.
    *
    * @param id     the identifier of the resource
    * @param rev    the last known revision of the resource instance
    * @param value  the json payload of the resource
    * @return a [[RefVersioned]] instance wrapped in the abstract ''F[_]'' type
    *         if successful, or a [[ch.epfl.bluebrain.nexus.admin.core.Fault]] wrapped within ''F[_]'' otherwise
    */
  def update(id: A, rev: Long, value: Json)(implicit caller: Caller): F[RefVersioned[A]] =
    for {
      _ <- validate(id, value)
      r <- evaluate(UpdateResource(id, rev, caller.meta, tags + id.persistenceId, value),
                    id.persistenceId,
                    s"Update res '$id'")
    } yield RefVersioned(id, r.rev)

  /**
    * Attempts to deprecate a resource locking it for further changes and blocking any attempts to create instances conforming to its
    * definition.
    *
    * @param id     the identifier of the resource
    * @param rev    the last known revision of the resource instance
    * @return a [[RefVersioned]] instance wrapped in the abstract ''F[_]'' type
    *         if successful, or a [[ch.epfl.bluebrain.nexus.admin.core.Fault]] wrapped within ''F[_]'' otherwise
    */
  def deprecate(id: A, rev: Long)(implicit caller: Caller): F[RefVersioned[A]] =
    evaluate(DeprecateResource(id, rev, caller.meta, tags + id.persistenceId), id.persistenceId, s"Deprecate res '$id'")
      .map(r => RefVersioned(id, r.rev))

  /**
    * Queries the system for the latest revision of the resource identified by the argument ''persId''.
    * The (in)existence of the resource is represented by the [[scala.Option]] type wrapped within the ''F[_]'' context.
    *
    * @param id     the identifier of the resource
    * @return an optional [[Resource]] instance wrapped in the
    *         abstract ''F[_]'' type if successful, or a [[ch.epfl.bluebrain.nexus.admin.core.Fault]] wrapped within
    *         ''F[_]'' otherwise
    */
  def fetch(id: A): F[Option[Resource[A]]] =
    agg.currentState(id.persistenceId).map {
      case Initial    => None
      case c: Current => Some(Resource(id, c.uuid, c.rev, c.value, c.deprecated))
    }

  /**
    * Queries the system for a specific ''revision'' of the resource identified by the argument ''persId''.
    * The (in)existence of the resource is represented by the [[scala.Option]] type wrapped within the ''F[_]'' context.
    *
    * @param id     the identifier of the resource
    * @param rev    the revision attempted to be fetched
    * @return an optional [[Resource]] instance wrapped in the
    *         abstract ''F[_]'' type if successful, or a [[ch.epfl.bluebrain.nexus.admin.core.Fault]] wrapped within
    *         ''F[_]'' otherwise
    */
  def fetch(id: A, rev: Long): F[Option[Resource[A]]] =
    stateAt(id.persistenceId, rev).map {
      case c: Current if c.rev == rev => Some(Resource(id, c.uuid, c.rev, c.value, c.deprecated))
      case _                          => None
    }

  /**
    * List resources which meet criteria specified by query and pagination parameters
    * @param query      query specifying criteria for resources to return
    * @param pagination pagination
    * @return  list of resources
    */
  def list(query: QueryPayload, pagination: Pagination)(
      implicit acls: HasReadProjects,
      idRes: IdResolvable[ProjectReference],
      rs: HttpClient[F, org.apache.jena.query.ResultSet]): F[QueryResults[Id]] = {
    val sparqlQuery = FilteredQuery[A](query, pagination, acls)
    rsToQueryResults[F](sparqlClient.queryRs(sparqlQuery), query.q.isDefined)
  }

  private def stateAt(persId: String, rev: Long): F[ResourceState] =
    agg.foldLeft[ResourceState](persId, Initial) {
      case (state, ev) if ev.rev <= rev => next(state, ev)
      case (state, _)                   => state
    }

  protected def evaluate(cmd: ResourceCommand, persId: String, intent: => String): F[Current] =
    F.pure {
      logger.debug(s"$intent: evaluating command '$cmd''")
    } flatMap { _ =>
      agg.eval(persId, cmd)
    } flatMap {
      case Left(rejection) =>
        logger.debug(s"$intent: command '$cmd' was rejected due to '$rejection'")
        F.raiseError(CommandRejected(rejection))
      // $COVERAGE-OFF$
      case Right(s @ Initial) =>
        logger.error(s"$intent: command '$cmd' evaluation failed, received an '$s' state")
        F.raiseError(Unexpected(s"Unexpected Initial state as outcome of evaluating command '$cmd'"))
      // $COVERAGE-ON$
      case Right(state: Current) =>
        logger.debug(s"$intent: command '$cmd' evaluation succeeded, generated state: '$state'")
        F.pure(state)
    }

  protected implicit def toId(id: A): Id = (id.namespace, id.reference).id

}

object Resources {

  type Agg[F[_]] = Aggregate[F] {
    type Identifier = String
    type Event      = ResourceEvent
    type State      = ResourceState
    type Command    = ResourceCommand
    type Rejection  = ResourceRejection
  }
}

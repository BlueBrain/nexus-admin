package ch.epfl.bluebrain.nexus.admin.core.projects

import cats.MonadError
import cats.instances.all._
import cats.syntax.all._
import ch.epfl.bluebrain.nexus.admin.core.CallerCtx
import ch.epfl.bluebrain.nexus.admin.core.CommonRejections.IllegalPayload
import ch.epfl.bluebrain.nexus.admin.core.Fault.{CommandRejected, Unexpected}
import ch.epfl.bluebrain.nexus.admin.core.config.AppConfig._
import ch.epfl.bluebrain.nexus.admin.core.projects.Project._
import ch.epfl.bluebrain.nexus.admin.core.projects.Projects._
import ch.epfl.bluebrain.nexus.admin.core.resources.ResourceRejection._
import ch.epfl.bluebrain.nexus.admin.core.resources.ResourceState.Eval
import ch.epfl.bluebrain.nexus.admin.core.resources.Resources.Agg
import ch.epfl.bluebrain.nexus.admin.core.resources._
import ch.epfl.bluebrain.nexus.admin.core.types.NamespaceOps._
import ch.epfl.bluebrain.nexus.admin.core.types.Ref._
import ch.epfl.bluebrain.nexus.admin.core.types.RefVersioned
import ch.epfl.bluebrain.nexus.admin.ld.Const._
import ch.epfl.bluebrain.nexus.admin.ld.IdOps._
import ch.epfl.bluebrain.nexus.admin.ld.{IdRef, IdResolvable, JsonLD}
import ch.epfl.bluebrain.nexus.admin.refined.permissions._
import ch.epfl.bluebrain.nexus.admin.refined.project._
import ch.epfl.bluebrain.nexus.commons.shacl.validator.ShaclValidatorErr.{CouldNotFindImports, IllegalImportDefinition}
import ch.epfl.bluebrain.nexus.commons.shacl.validator.{ShaclSchema, ShaclValidator}
import io.circe.Json
import io.circe.syntax._
import journal.Logger

/**
  * Bundles operations that can be performed against a project using the underlying persistence abstraction.
  *
  * @param resources the underlying generic resource operations
  * @param F         a MonadError typeclass instance for ''F[_]''
  * @tparam F the monadic effect type
  */
class Projects[F[_]](resources: Resources[F, ProjectReference])(implicit F: MonadError[F, Throwable],
                                                                idRes: IdResolvable[ProjectReference],
                                                                config: ProjectsConfig) {

  private val tags = Set("project")

  /**
    * Attempts to create a new project instance.
    *
    * @param reference the name of the project
    * @param value     the payload of the project
    * @return a [[RefVersioned]] instance wrapped in the abstract ''F[_]'' type
    *         if successful, or a [[ch.epfl.bluebrain.nexus.admin.core.Fault]] wrapped within ''F[_]'' otherwise
    */
  def create(reference: ProjectReference, value: Json)(implicit ctx: CallerCtx,
                                                       perms: HasCreateProjects): F[RefVersioned[ProjectReference]] =
    resources.create(reference, value)(tags, reference.toPersId)

  /**
    * Attempts to update an existing project instance with a new json payload.
    *
    * @param reference the name of the project
    * @param rev       the last known revision of the project instance
    * @param value     the payload of the project
    * @return a [[RefVersioned]] instance wrapped in the abstract ''F[_]'' type
    *         if successful, or a [[ch.epfl.bluebrain.nexus.admin.core.Fault]] wrapped within ''F[_]'' otherwise
    */
  def update(reference: ProjectReference, rev: Long, value: Json)(
      implicit ctx: CallerCtx,
      perms: HasWriteProjects): F[RefVersioned[ProjectReference]] =
    resources.update(reference, rev, value.asJson)(tags, reference.toPersId)

  /**
    * Attempts to deprecate a project locking it for further changes and blocking any attempts to create instances conforming to its
    * definition.
    *
    * @param reference the name of the project
    * @param rev    the last known revision of the project instance
    * @return a [[RefVersioned]] instance wrapped in the abstract ''F[_]'' type
    *         if successful, or a [[ch.epfl.bluebrain.nexus.admin.core.Fault]] wrapped within ''F[_]'' otherwise
    */
  def deprecate(reference: ProjectReference, rev: Long)(implicit ctx: CallerCtx,
                                                        perms: HasWriteProjects): F[RefVersioned[ProjectReference]] =
    resources.deprecate(reference, rev)(tags, reference.toPersId)

  /**
    * Queries the system for the latest revision of the project identified by the argument ''reference''.
    * The (in)existence of the project is represented by the [[scala.Option]] type wrapped within the ''F[_]'' context.
    *
    * @param reference the name of the project
    * @return an optional [[Project]] instance wrapped in the
    *         abstract ''F[_]'' type if successful, or a [[ch.epfl.bluebrain.nexus.admin.core.Fault]] wrapped within
    *         ''F[_]'' otherwise
    */
  def fetch(reference: ProjectReference)(implicit perms: HasReadProjects): F[Option[Project]] =
    resources.fetch(reference)(reference.toPersId)

  /**
    * Queries the system for a specific ''revision'' of the project identified by the argument ''reference''.
    * The (in)existence of the represented is represented by the [[scala.Option]] type wrapped within the ''F[_]'' context.
    *
    * @param reference the name of the project
    * @return an optional [[Project]] instance wrapped in the
    *         abstract ''F[_]'' type if successful, or a [[ch.epfl.bluebrain.nexus.admin.core.Fault]] wrapped within
    *         ''F[_]'' otherwise
    */
  def fetch(reference: ProjectReference, rev: Long)(implicit perms: HasReadProjects): F[Option[Project]] =
    resources.fetch(reference, rev)(reference.toPersId)

  /**
    * Asserts the project exists and it allows modifications on children resources.
    *
    * @param reference the name of the project
    * @return () or the appropriate rejection in the ''F'' context
    */
  def validateUnlocked(reference: ProjectReference): F[Unit] =
    resources.validateUnlocked(reference.toPersId)

  private implicit class IdRefSyntax(reference: ProjectReference) {
    lazy val toPersId: String = {
      //val idRef: IdRef = refToResolvable.apply(reference)
      val idRef: IdRef = idRes(reference)
      s"${idRef.namespace.host.hashCode.abs.toString.take(5)}-${reference.value}"
    }
  }

  private implicit def toProject(resource: F[Option[Resource[ProjectReference]]]): F[Option[Project]] =
    resource.flatMap {
      case Some(Resource(id, rev, value, deprecated)) =>
        value.as[ProjectValue] match {
          case Right(v)  => F.pure(Some(Project(id, rev, v, value, deprecated)))
          case Left(err) =>
            // $COVERAGE-OFF$
            logger.error(s"Could not convert json value '$value' to Value", err)
            F.raiseError(Unexpected(s"Could not convert json value '$value' to Value"))
          // $COVERAGE-ON$
        }
      case None => F.pure(None)
    }
}

object Projects {

  private[projects] implicit val logger: Logger = Logger[this.type]

  /**
    * Constructs a new ''Projects'' instance that bundles operations that can be performed against projects using the
    * underlying persistence abstraction.
    *
    * @param agg    the aggregate definition
    * @param F      a MonadError typeclass instance for ''F[_]''
    * @param config the project specific settings
    * @tparam F the monadic effect type
    */
  final def apply[F[_]](agg: Agg[F])(implicit F: MonadError[F, Throwable],
                                     config: ProjectsConfig,
                                     validator: ShaclValidator[F]): Projects[F] =
    new Projects(new Resources[F, ProjectReference](agg) {

      override def validateCreate(id: ProjectReference, value: Json): F[Unit] = validate(value)

      override def validateUpdate(id: ProjectReference, value: Json): F[Unit] = validate(value)

      private def validate(value: JsonLD): F[Unit] = {
        //TODO: This assumes that the `@type` field is found on the top of the JSON tree.
        //Come up with a proper JSON-lD solution for it in following commits.
        val tpeJson = Json.obj(`@type` -> Json.arr((value.tpe + nxv.Project).map(_.id.asJson).toSeq: _*))
        val merged  = value.appendContext(projectContext) deepMerge tpeJson
        validator(ShaclSchema(projectSchema), merged)
          .flatMap { report =>
            if (report.conforms) F.pure(())
            else F.raiseError[Unit](CommandRejected(ShapeConstraintViolations(report.result.map(_.reason))))
          }
          .recoverWith {
            case CouldNotFindImports(missing)     => F.raiseError(CommandRejected(MissingImportsViolation(missing)))
            case IllegalImportDefinition(missing) => F.raiseError(CommandRejected(IllegalImportsViolation(missing)))
          }
      }

    })

  private[projects] class EvalProject(implicit config: ProjectsConfig) extends Eval {
    override def updateResourceAfter(state: ResourceState.Current,
                                     c: ResourceCommand.UpdateResource): Either[ResourceRejection, ResourceEvent] = {
      (state.value.as[ProjectValue], c.value.as[ProjectValue])
        .mapN {
          case (value, updatedValue) =>
            if (updatedValue.prefixMappings.containsMappings(value.prefixMappings))
              super.updateResourceAfter(state, c)
            else
              Left(
                WrappedRejection(
                  IllegalPayload(
                    "Invalid 'prefixMappings' object",
                    Some("The 'prefixMappings' values cannot be overridden but just new values can be appended."))))
        }
        .getOrElse(super.updateResourceAfter(state, c))
    }
  }

  object EvalProject {
    final def apply()(implicit config: ProjectsConfig): EvalProject = new EvalProject
  }
}

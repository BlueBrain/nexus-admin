package ch.epfl.bluebrain.nexus.admin.projects

import java.util.UUID

import cats.effect.Async
import ch.epfl.bluebrain.nexus.admin.config.AppConfig.HttpConfig
import ch.epfl.bluebrain.nexus.admin.projects.ProjectCommand._
import ch.epfl.bluebrain.nexus.admin.projects.ProjectEvent._
import ch.epfl.bluebrain.nexus.admin.projects.ProjectRejection._
import ch.epfl.bluebrain.nexus.admin.types.{ResourceF, ResourceMetadata}
import ch.epfl.bluebrain.nexus.commons.types.Meta

sealed trait ProjectState extends Product with Serializable

object ProjectState {

  /**
    * Initial state for all resources.
    */
  final case object Initial extends ProjectState

  /**
    * State used for all resources that have been created and later possibly updated or deprecated.
    *
    * @param id           the permanent identifier for the resource
    * @param organization the permanent identifier of the parent organization
    * @param label        the label (segment) of the resource
    * @param description  an optional project description
    * @param rev          the selected revision number
    * @param meta         the metadata associated to this resource
    * @param deprecated   the deprecation status
    */
  final case class Current(id: UUID,
                           organization: UUID,
                           label: ProjectLabel,
                           description: Option[String],
                           rev: Long,
                           meta: Meta,
                           deprecated: Boolean)
      extends ProjectState {

    def toResource(implicit http: HttpConfig): ResourceF[SimpleProject] =
      ResourceF(label.toIri,
                id,
                rev,
                deprecated,
                types,
                meta.instant,
                meta.author,
                meta.instant,
                meta.author,
                SimpleProject(label.value, description))

    def toResourceMetaData(implicit http: HttpConfig): ResourceMetadata =
      ResourceF.unit(label.toIri, id, rev, deprecated, types, meta.instant, meta.author, meta.instant, meta.author)
  }

  /**
    * State transition function for resources; considering a current state (the ''state'' argument) and an emitted
    * ''event'' it computes the next state.
    *
    * @param state the current state
    * @param event the emitted event
    * @return the next state
    */
  def next(state: ProjectState, event: ProjectEvent): ProjectState = {

    (state, event: ProjectEvent) match {
      case (Initial, ProjectCreated(id, org, label, rev, meta, value)) =>
        Current(id, org, label, rev, meta, value, deprecated = false)
      // $COVERAGE-OFF$
      case (Initial, _) => Initial
      // $COVERAGE-ON$
      case (c: Current, _) if c.deprecated => c
      case (c, _: ProjectCreated)          => c
      case (c: Current, ProjectUpdated(_, label, desc, rev, meta)) =>
        c.copy(label = label, description = desc, rev = rev, meta = meta)
      case (c: Current, ProjectDeprecated(_, rev, meta)) =>
        c.copy(rev = rev, meta = meta, deprecated = true)
    }
  }

  object Eval {

    def createProject(state: ProjectState, c: CreateProject): Either[ProjectRejection, ProjectEvent] =
      state match {
        case Initial => Right(ProjectCreated(c.id, c.organization, c.label, c.description, 1L, c.meta))
        case _       => Left(ProjectAlreadyExists)
      }

    def updateProject(state: ProjectState, c: UpdateProject): Either[ProjectRejection, ProjectEvent] =
      state match {
        case Initial                      => Left(ProjectDoesNotExists)
        case s: Current if s.rev != c.rev => Left(IncorrectRevisionProvided)
        case s: Current if s.deprecated   => Left(ProjectIsDeprecated)
        case s: Current                   => updateProjectAfter(s, c)
      }

    def updateProjectAfter(state: Current, c: UpdateProject): Either[ProjectRejection, ProjectEvent] =
      Right(ProjectUpdated(state.id, c.label, c.description, state.rev + 1, c.meta))

    def deprecateProject(state: ProjectState, c: DeprecateProject): Either[ProjectRejection, ProjectEvent] =
      state match {
        case Initial                      => Left(ProjectDoesNotExists)
        case s: Current if s.rev != c.rev => Left(IncorrectRevisionProvided)
        case s: Current if s.deprecated   => Left(ProjectIsDeprecated)
        case s: Current                   => deprecateProjectAfter(s, c)
      }

    def deprecateProjectAfter(state: Current, c: DeprecateProject): Either[ProjectRejection, ProjectEvent] =
      Right(ProjectDeprecated(state.id, state.rev + 1, c.meta))

    /**
      * Command evaluation logic for projects; considering a current ''state'' and a command to be evaluated either
      * reject the command or emit a new event that characterizes the change for an aggregate.
      *
      * @param state the current state
      * @param cmd   the command to be evaluated
      * @return either a rejection or the event emitted
      */
    final def apply[F[_]](state: ProjectState, cmd: ProjectCommand)(
        implicit F: Async[F]): F[Either[ProjectRejection, ProjectEvent]] = {

      cmd match {
        case c: CreateProject    => F.pure(createProject(state, c))
        case c: UpdateProject    => F.pure(updateProject(state, c))
        case c: DeprecateProject => F.pure(deprecateProject(state, c))
      }
    }
  }
}

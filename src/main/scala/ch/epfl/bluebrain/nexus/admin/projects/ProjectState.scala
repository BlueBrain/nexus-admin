package ch.epfl.bluebrain.nexus.admin.projects

import java.time.Instant
import java.util.UUID

import cats.effect.Async
import ch.epfl.bluebrain.nexus.admin.projects.ProjectCommand._
import ch.epfl.bluebrain.nexus.admin.projects.ProjectEvent._
import ch.epfl.bluebrain.nexus.admin.projects.ProjectRejection._
import ch.epfl.bluebrain.nexus.commons.types.identity.Identity

sealed trait ProjectState extends Product with Serializable

object ProjectState {

  /**
    * Initial state for all resources.
    */
  final case object Initial extends ProjectState

  /**
    * State used for all resources that have been created and later possibly updated or deprecated.
    *
    * @param id           the permanent identifier for the project
    * @param organization the permanent identifier of the parent organization
    * @param label        the label (segment) of the resource
    * @param description  an optional project description
    * @param rev          the selected revision number
    * @param instant      the timestamp associated with this state
    * @param subject      the identity associated with this state
    * @param deprecated   the deprecation status
    */
  final case class Current(id: UUID,
                           organization: UUID,
                           label: String,
                           description: Option[String],
                           rev: Long,
                           instant: Instant,
                           subject: Identity,
                           deprecated: Boolean)
      extends ProjectState

  /**
    * State transition function for resources; considering a current state (the ''state'' argument) and an emitted
    * ''event'' it computes the next state.
    *
    * @param state the current state
    * @param event the emitted event
    * @return the next state
    */
  def next(state: ProjectState, event: ProjectEvent): ProjectState = (state, event) match {
    case (Initial, ProjectCreated(id, org, label, rev, instant, subject, value)) =>
      Current(id, org, label, rev, instant, subject, value, deprecated = false)
    // $COVERAGE-OFF$
    case (Initial, _) => Initial
    // $COVERAGE-ON$
    case (c: Current, _) if c.deprecated => c
    case (c, _: ProjectCreated)          => c
    case (c: Current, ProjectUpdated(_, label, desc, rev, instant, subject)) =>
      c.copy(label = label, description = desc, rev = rev, instant = instant, subject = subject)
    case (c: Current, ProjectDeprecated(_, rev, instant, subject)) =>
      c.copy(rev = rev, instant = instant, subject = subject, deprecated = true)
  }

  private[projects] object Eval {

    private def createProject(state: ProjectState, c: CreateProject): Either[ProjectRejection, ProjectEvent] =
      state match {
        case Initial => Right(ProjectCreated(c.id, c.organization, c.label, c.description, 1L, c.instant, c.subject))
        case _       => Left(ProjectAlreadyExists)
      }

    private def updateProject(state: ProjectState, c: UpdateProject): Either[ProjectRejection, ProjectEvent] =
      state match {
        case Initial                      => Left(ProjectDoesNotExists)
        case s: Current if s.rev != c.rev => Left(IncorrectRev(c.rev))
        case s: Current if s.deprecated   => Left(ProjectIsDeprecated)
        case s: Current                   => updateProjectAfter(s, c)
      }

    private def updateProjectAfter(state: Current, c: UpdateProject): Either[ProjectRejection, ProjectEvent] =
      Right(ProjectUpdated(state.id, c.label, c.description, state.rev + 1, c.instant, c.subject))

    private def deprecateProject(state: ProjectState, c: DeprecateProject): Either[ProjectRejection, ProjectEvent] =
      state match {
        case Initial                      => Left(ProjectDoesNotExists)
        case s: Current if s.rev != c.rev => Left(IncorrectRev(c.rev))
        case s: Current if s.deprecated   => Left(ProjectIsDeprecated)
        case s: Current                   => deprecateProjectAfter(s, c)
      }

    private def deprecateProjectAfter(state: Current, c: DeprecateProject): Either[ProjectRejection, ProjectEvent] =
      Right(ProjectDeprecated(state.id, state.rev + 1, c.instant, c.subject))

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

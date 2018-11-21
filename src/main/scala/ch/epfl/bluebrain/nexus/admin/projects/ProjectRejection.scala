package ch.epfl.bluebrain.nexus.admin.projects

import ch.epfl.bluebrain.nexus.admin.CommonRejection
import ch.epfl.bluebrain.nexus.commons.types.Rejection

sealed trait ProjectRejection extends Rejection

object ProjectRejection {

  /**
    * Signals an error while decoding a project JSON payload.
    *
    * @param message human readable error details
    */
  final case class InvalidProjectFormat(message: String) extends ProjectRejection

  /**
    * Signals that a project cannot be created because one with the same identifier already exists.
    */
  final case object ProjectAlreadyExists extends ProjectRejection

  /**
    * Signals that an operation on a project cannot be performed due to the fact that the referenced project does not exist.
    */
  final case object ProjectDoesNotExists extends ProjectRejection

  /**
    * Signals that an operation on a project cannot be performed due to the fact that the referenced parent organization does not exist.
    */
  final case object OrganizationDoesNotExist extends ProjectRejection

  /**
    * Signals that a project update cannot be performed due its deprecation status.
    */
  final case object ProjectIsDeprecated extends ProjectRejection

  /**
    * Signals that a project update cannot be performed due to an incorrect revision provided.
    */
  final case object IncorrectRevisionProvided extends ProjectRejection

  /**
    * Signals that an unexpected state was encountered performing a project action.
    */
  final case object UnexpectedState extends ProjectRejection

  /**
    * Signals any other rejection which gets defined out of this scope.
    *
    * @param rejection the underlying rejections that was triggered
    */
  final case class WrappedRejection(rejection: CommonRejection) extends ProjectRejection

}

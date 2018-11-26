package ch.epfl.bluebrain.nexus.admin.projects

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
    *
    * @param rev the provided revision
    */
  final case class IncorrectRev(rev: Long) extends ProjectRejection

}

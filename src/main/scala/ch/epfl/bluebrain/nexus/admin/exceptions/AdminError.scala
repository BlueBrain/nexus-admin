package ch.epfl.bluebrain.nexus.admin.exceptions

import ch.epfl.bluebrain.nexus.commons.types.Err

object AdminError {

  /**
    * Signals that the resource is in an unexpected state.
    *
    * @param id ID of the resource
    */
  @SuppressWarnings(Array("IncorrectlyNamedExceptions"))
  final case class UnexpectedState(id: String) extends Err(s"Unexpected resource state for resource with ID $id")

  /**
    * Signals that an unexpected error.
    *
    * @param message the exception message
    */
  @SuppressWarnings(Array("IncorrectlyNamedExceptions"))
  final case class UnexpectedError(override val message: String) extends Err(message)
}

package ch.epfl.bluebrain.nexus.admin.exceptions
import ch.epfl.bluebrain.nexus.commons.types.Err

/**
  *  Exception signalling that the resource is in an unexpected state
  * @param id ID of the resource
  */
@SuppressWarnings(Array("IncorrectlyNamedExceptions"))
class UnexpectedState(id: String) extends Err(s"Unexpected resource state for resource with ID $id")

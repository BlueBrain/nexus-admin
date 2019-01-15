package ch.epfl.bluebrain.nexus.admin.exceptions

/**
  * Generic error types global to the entire service.
  *
  * @param msg the reason why the error occurred
  */
@SuppressWarnings(Array("IncorrectlyNamedExceptions"))
sealed abstract class AdminError(val msg: String) extends Exception with Product with Serializable {
  override def fillInStackTrace(): Throwable = this
  override def getMessage: String            = msg
}
object AdminError {

  /**
    * Signals that the resource is in an unexpected state.
    *
    * @param id ID of the resource
    */
  @SuppressWarnings(Array("IncorrectlyNamedExceptions"))
  final case class UnexpectedState(id: String) extends AdminError(s"Unexpected resource state for resource with ID $id")

  /**
    * Signals that an unexpected error.
    *
    * @param message the exception message
    */
  @SuppressWarnings(Array("IncorrectlyNamedExceptions"))
  final case class UnexpectedError(message: String) extends AdminError(message)

  /**
    * Generic wrapper for iam errors that should not be exposed to clients.
    *
    * @param reason the underlying error reason
    */
  @SuppressWarnings(Array("IncorrectlyNamedExceptions"))
  final case class InternalError(reason: String)
      extends AdminError(s"An internal server error occurred due to '$reason'.")
}

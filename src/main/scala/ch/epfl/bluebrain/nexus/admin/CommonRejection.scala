package ch.epfl.bluebrain.nexus.admin

import ch.epfl.bluebrain.nexus.commons.types.{Err, Rejection}

/**
  * Enumeration type for rejections returned when a generic rejection occurs.
  */
sealed trait CommonRejection extends Rejection

object CommonRejection {

  /**
    * Signals the inability to convert the requested query parameter.
    */
  @SuppressWarnings(Array("IncorrectlyNamedExceptions"))
  final case class IllegalParameter(override val message: String) extends Err(message) with CommonRejection

  /**
    * Signals the inability to convert the requested payload.
    */
  @SuppressWarnings(Array("IncorrectlyNamedExceptions"))
  final case class IllegalPayload(override val message: String, details: Option[String])
      extends Err(message)
      with CommonRejection

  /**
    * Signals the inability to connect to an underlying service to perform a request
    *
    * @param message a human readable description of the cause
    */
  @SuppressWarnings(Array("IncorrectlyNamedExceptions"))
  final case class DownstreamServiceError(override val message: String) extends Err(message) with CommonRejection

  /**
    * Signals that the provided client URI does not match any service endpoint
    */
  @SuppressWarnings(Array("IncorrectlyNamedExceptions"))
  final case object InvalidResourceIri
      extends Err("Provided IRI does not match any service endpoint")
      with CommonRejection

}

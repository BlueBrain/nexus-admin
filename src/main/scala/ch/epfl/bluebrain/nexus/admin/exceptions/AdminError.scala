package ch.epfl.bluebrain.nexus.admin.exceptions

import akka.http.scaladsl.model.StatusCodes
import ch.epfl.bluebrain.nexus.admin.config.Contexts._
import ch.epfl.bluebrain.nexus.rdf.syntax.circe.context._
import ch.epfl.bluebrain.nexus.service.http.directives.StatusFrom
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.semiauto.deriveEncoder
import io.circe.{Encoder, Json}

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

@SuppressWarnings(Array("IncorrectlyNamedExceptions"))
object AdminError {

  /**
    * Signals that the resource is in an unexpected state.
    *
    * @param id ID of the resource
    */
  final case class UnexpectedState(id: String) extends AdminError(s"Unexpected resource state for resource with ID $id")

  /**
    * Signals that an unexpected error.
    *
    * @param reason the exception message
    */
  final case class UnexpectedError(reason: String) extends AdminError(reason)

  /**
    * Generic wrapper for iam errors that should not be exposed to clients.
    *
    * @param reason the underlying error reason
    */
  final case class InternalError(reason: String) extends AdminError(reason)

  /**
    * Signals that the requested resource was not found
    */
  final case object NotFound extends AdminError("The requested resource could not be found.")

  implicit val adminErrorEncoder: Encoder[AdminError] = {
    implicit val rejectionConfig: Configuration = Configuration.default.withDiscriminator("@type")
    val enc                                     = deriveEncoder[AdminError].mapJson(_ addContext errorCtxUri)
    Encoder.instance(r => enc(r) deepMerge Json.obj("reason" -> Json.fromString(r.msg)))
  }

  implicit val adminErrorStatusFrom: StatusFrom[AdminError] = {
    case NotFound => StatusCodes.NotFound
    case _        => StatusCodes.InternalServerError
  }
}

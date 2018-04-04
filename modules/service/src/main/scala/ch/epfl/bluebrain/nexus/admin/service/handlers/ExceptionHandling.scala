package ch.epfl.bluebrain.nexus.admin.service.handlers

import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.server.Directives.complete
import akka.http.scaladsl.server.ExceptionHandler
import ch.epfl.bluebrain.nexus.admin.core.CommonRejections
import ch.epfl.bluebrain.nexus.admin.core.Fault.{CommandRejected, Unexpected}
import ch.epfl.bluebrain.nexus.admin.core.resources.ResourceRejection
import ch.epfl.bluebrain.nexus.admin.core.resources.ResourceRejection._
import ch.epfl.bluebrain.nexus.commons.http.ContextUri
import ch.epfl.bluebrain.nexus.commons.http.JsonLdCirceSupport.OrderedKeys
import ch.epfl.bluebrain.nexus.service.http.directives.ErrorDirectives._
import ch.epfl.bluebrain.nexus.service.http.directives.StatusFrom
import io.circe.Encoder
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.auto._
import io.circe.generic.extras.semiauto.deriveEncoder
import io.circe.syntax._
import journal.Logger

/**
  * Total exception handling logic for the service.
  * It provides an exception handler implementation that ensures
  * all rejections and unexpected failures are gracefully handled
  * and presented to the caller.
  */
object ExceptionHandling {

  private val logger                         = Logger[this.type]
  private implicit val config: Configuration = Configuration.default.withDiscriminator("code")

  /**
    * @param errorContext     the context URI to be injected in the JSON-LD error responses
    * @param errorOrderedKeys the implicitly available JSON keys ordering on response payload
    * @return an ExceptionHandler for [[ch.epfl.bluebrain.nexus.admin.core.Fault]] subtypes that ensures a descriptive
    *         message is returned to the caller
    */
  final def exceptionHandler(implicit errorContext: ContextUri, errorOrderedKeys: OrderedKeys): ExceptionHandler =
    ExceptionHandler {
      case CommandRejected(r: ResourceRejection) => complete(r)
      case CommandRejected(r: CommonRejections)  => complete(r)

      // $COVERAGE-OFF$
      case Unexpected(reason) =>
        logger.warn(s"An unexpected rejection has happened '$reason'")
        complete(InternalError())
      // $COVERAGE-ON$
    }

  private implicit val wrappedRejectionEnc: Encoder[ResourceRejection] = {
    val enc = deriveEncoder[ResourceRejection]
    Encoder.encodeJson.contramap {
      case WrappedRejection(rej) => rej.asJson
      case other                 => enc(other)
    }
  }

  /**
    * The discriminator is enough to give us a Json representation (the name of the class)
    */
  private implicit val resourceStatusFrom: StatusFrom[ResourceRejection] =
    StatusFrom {
      case IncorrectRevisionProvided    => Conflict
      case ResourceAlreadyExists        => Conflict
      case ResourceDoesNotExists        => NotFound
      case ParentResourceDoesNotExists  => NotFound
      case ResourceIsDeprecated         => BadRequest
      case _: MissingImportsViolation   => BadRequest
      case _: IllegalImportsViolation   => BadRequest
      case _: WrappedRejection          => BadRequest
      case _: ShapeConstraintViolations => BadRequest
    }

  private implicit val commonFrom: StatusFrom[CommonRejections] =
    StatusFrom(_ => BadRequest)

  private implicit val internalErrorStatusFrom: StatusFrom[InternalError] =
    StatusFrom(_ => InternalServerError)

  /**
    * An internal error representation that can safely be returned in its json form to the caller.
    *
    * @param code the code displayed as a response (InternalServerError as default)
    */
  private final case class InternalError(code: String = "InternalServerError")

}

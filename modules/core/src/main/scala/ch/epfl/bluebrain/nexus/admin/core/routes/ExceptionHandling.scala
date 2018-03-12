package ch.epfl.bluebrain.nexus.admin.core.routes

import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.server.Directives.complete
import akka.http.scaladsl.server.ExceptionHandler
import ch.epfl.bluebrain.nexus.admin.core.Fault.{CommandRejected, Unexpected}
import ch.epfl.bluebrain.nexus.admin.core.rejections.CommonRejections
import ch.epfl.bluebrain.nexus.admin.core.rejections.CommonRejections._
import ch.epfl.bluebrain.nexus.admin.core.resources.ResourceRejection
import ch.epfl.bluebrain.nexus.admin.core.resources.ResourceRejection._
import ch.epfl.bluebrain.nexus.admin.core.routes.ExceptionHandling.InternalError
import ch.epfl.bluebrain.nexus.commons.http.ContextUri
import ch.epfl.bluebrain.nexus.commons.types.HttpRejection
import ch.epfl.bluebrain.nexus.commons.types.HttpRejection._
import ch.epfl.bluebrain.nexus.service.http.directives.ErrorDirectives._
import ch.epfl.bluebrain.nexus.service.http.directives.StatusFrom
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.auto._
import journal.Logger

/**
  * Total exception handling logic for the service.
  * It provides an exception handler implementation that ensures
  * all rejections and unexpected failures are gracefully handled
  * and presented to the caller.
  */
class ExceptionHandling(implicit errorContext: ContextUri) {

  private val logger = Logger[this.type]

  private implicit val config: Configuration = Configuration.default.withDiscriminator("code")

  private final def exceptionHandler: ExceptionHandler = ExceptionHandler {
    case CommandRejected(r: ResourceRejection) => complete(r)
    case CommandRejected(r: IllegalParam)      => complete(r: CommonRejections)
    case UnauthorizedAccess                    => complete(UnauthorizedAccess: HttpRejection)

    // $COVERAGE-OFF$
    case Unexpected(reason) =>
      logger.warn(s"An unexpected rejection has happened '$reason'")
      complete(InternalError())
    // $COVERAGE-ON$
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
      case _: ShapeConstraintViolations => BadRequest
    }

  private implicit val httpRejections: StatusFrom[HttpRejection] =
    StatusFrom {
      case UnauthorizedAccess    => Unauthorized
      case MethodNotSupported(_) => MethodNotAllowed
      case _                     => BadRequest
    }

  private implicit val commonFrom: StatusFrom[CommonRejections] =
    StatusFrom(_ => BadRequest)

  private implicit val internalErrorStatusFrom: StatusFrom[InternalError] =
    StatusFrom(_ => InternalServerError)

}

object ExceptionHandling {

  /**
    * @param errorContext the context URI to be injected in the JSON-LD error responses
    * @return an ExceptionHandler for [[ch.epfl.bluebrain.nexus.admin.core.Fault]] subtypes that ensures a descriptive
    *         message is returned to the caller
    */
  final def exceptionHandler(errorContext: ContextUri): ExceptionHandler = {
    val handler = new ExceptionHandling()(errorContext)
    handler.exceptionHandler
  }

  /**
    * An internal error representation that can safely be returned in its json form to the caller.
    *
    * @param code the code displayed as a response (InternalServerError as default)
    */
  private final case class InternalError(code: String = "InternalServerError")

}

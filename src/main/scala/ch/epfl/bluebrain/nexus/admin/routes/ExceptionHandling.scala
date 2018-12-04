package ch.epfl.bluebrain.nexus.admin.routes
import akka.http.scaladsl.server.Directives.complete
import akka.http.scaladsl.server.ExceptionHandler
import ch.epfl.bluebrain.nexus.admin.exceptions.AdminError.UnexpectedError
import journal.Logger

import scala.util.Try

object ExceptionHandling {

  private val logger = Logger[this.type]

  /**
    * An ExceptionHandler that ensures a descriptive message is returned to the caller
    */
  val handler: ExceptionHandler =
    ExceptionHandler {
      case err =>
        logger.error("Exception caught during routes processing ", err)
        val msg = Try(err.getMessage).filter(_ != null).getOrElse("Something went wrong. Please, try again later.")
        complete(UnexpectedError(msg))
    }

}

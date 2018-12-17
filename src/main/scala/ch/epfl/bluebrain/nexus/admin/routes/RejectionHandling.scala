package ch.epfl.bluebrain.nexus.admin.routes

import akka.http.javadsl.server.AuthorizationFailedRejection
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.server.Directives.complete
import akka.http.scaladsl.server._
import ch.epfl.bluebrain.nexus.admin.CommonRejection.IllegalParameter
import ch.epfl.bluebrain.nexus.admin.directives.AuthDirectives.CustomAuthRejection
import ch.epfl.bluebrain.nexus.admin.marshallers.instances._
import ch.epfl.bluebrain.nexus.commons.types.HttpRejection
import ch.epfl.bluebrain.nexus.commons.types.HttpRejection._

object RejectionHandling {

  /**
    * Defines the custom handling of rejections. When multiple rejections are generated
    * in the routes evaluation process, the priority order to handle them is defined
    * by the order of appearance in this method.
    */
  val handler: RejectionHandler =
    RejectionHandler
      .newBuilder()
      .handle {
        case MalformedQueryParamRejection(_, _, Some(e: HttpRejection)) =>
          complete(e)
        case MalformedQueryParamRejection(_, _, Some(err)) =>
          complete(IllegalParameter(err.getMessage))
        case ValidationRejection(err, _) =>
          complete(IllegalParameter(err))
        case MissingQueryParamRejection(param) =>
          complete(MissingParameters(Seq(param)))
        case _: AuthorizationFailedRejection =>
          complete(Unauthorized -> UnauthorizedAccess)
        case CustomAuthRejection(e) =>
          complete(e)
      }
      .handleAll[MalformedRequestContentRejection] { rejection =>
        val aggregate = rejection.map(_.message).mkString(", ")
        complete(WrongOrInvalidJson(Some(aggregate)))
      }
      .handleAll[MethodRejection] { methodRejections =>
        val names = methodRejections.map(_.supported.name)
        complete(MethodNotAllowed -> MethodNotSupported(names))
      }
      .result()

}

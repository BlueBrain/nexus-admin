package ch.epfl.bluebrain.nexus.admin.service.routes

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.OAuth2BearerToken
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import ch.epfl.bluebrain.nexus.admin.core.CallerCtx._
import ch.epfl.bluebrain.nexus.admin.core.config.AppConfig
import ch.epfl.bluebrain.nexus.admin.core.config.AppConfig._
import ch.epfl.bluebrain.nexus.admin.core.projects.Project._
import ch.epfl.bluebrain.nexus.admin.core.projects.Projects
import ch.epfl.bluebrain.nexus.admin.core.types.Ref._
import ch.epfl.bluebrain.nexus.admin.core.types.RefVersioned._
import ch.epfl.bluebrain.nexus.admin.refined.permissions.{HasCreateProjects, HasReadProjects, HasWriteProjects}
import ch.epfl.bluebrain.nexus.admin.refined.project.ProjectReference
import ch.epfl.bluebrain.nexus.admin.service.directives.AuthDirectives._
import ch.epfl.bluebrain.nexus.admin.service.directives.RefinedDirectives._
import ch.epfl.bluebrain.nexus.commons.http.JsonLdCirceSupport.{marshallerHttp, jsonUnmarshaller}
import ch.epfl.bluebrain.nexus.commons.iam.IamClient
import ch.epfl.bluebrain.nexus.service.kamon.directives.TracingDirectives
import io.circe.Json

import scala.concurrent.Future

/**
  * Http route definitions for domain specific functionality.
  *
  * @param projects               the domain operation bundle
  */
final class ProjectRoutes(projects: Projects[Future])(implicit iamClient: IamClient[Future],
                                                      config: AppConfig,
                                                      tracing: TracingDirectives)
    extends BaseRouteSplit {

  import tracing._
  protected def readRoutes(implicit credentials: Option[OAuth2BearerToken]): Route =
    (segment(of[ProjectReference]) & pathEndOrSingleSlash) { name =>
      (get & authorizeOn[HasReadProjects](name.value)) { implicit perms =>
        parameter('rev.as[Long].?) {
          case Some(rev) =>
            trace("getProjectRev") {
              onSuccess(projects.fetch(name, rev)) {
                case Some(project) => complete(StatusCodes.OK -> project)
                case None          => complete(StatusCodes.NotFound)
              }
            }
          case None =>
            trace("getProject") {
              onSuccess(projects.fetch(name)) {
                case Some(project) => complete(StatusCodes.OK -> project)
                case None          => complete(StatusCodes.NotFound)
              }
            }
        }
      }
    }

  protected def writeRoutes(implicit credentials: Option[OAuth2BearerToken]): Route =
    (segment(of[ProjectReference]) & pathEndOrSingleSlash) { name =>
      (put & entity(as[Json])) { json =>
        authCaller.apply { implicit caller =>
          parameter('rev.as[Long].?) {
            case Some(rev) =>
              (trace("updateProject") & authorizeOn[HasWriteProjects](name.value)) { implicit perms =>
                onSuccess(projects.update(name, rev, json)) { ref =>
                  complete(StatusCodes.OK -> ref)
                }
              }
            case None =>
              (trace("createProject") & authorizeOn[HasCreateProjects](name.value)) { implicit perms =>
                onSuccess(projects.create(name, json)) { ref =>
                  complete(StatusCodes.Created -> ref)
                }
              }
          }
        }
      } ~
        delete {
          parameter('rev.as[Long]) { rev =>
            authCaller.apply { implicit caller =>
              (trace("deprecateProject") & authorizeOn[HasWriteProjects](name.value)) { implicit perms =>
                onSuccess(projects.deprecate(name, rev)) { ref =>
                  complete(StatusCodes.OK -> ref)
                }
              }
            }
          }
        }
    }

  def routes: Route = combinedRoutesFor("projects")

}

object ProjectRoutes {
  final def apply(projects: Projects[Future])(implicit iamClient: IamClient[Future],
                                              config: AppConfig): ProjectRoutes = {
    implicit val tracing = new TracingDirectives()
    new ProjectRoutes(projects)
  }
}

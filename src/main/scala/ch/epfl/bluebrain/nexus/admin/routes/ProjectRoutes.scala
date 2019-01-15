package ch.epfl.bluebrain.nexus.admin.routes

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import ch.epfl.bluebrain.nexus.admin.config.AppConfig.tracing.trace
import ch.epfl.bluebrain.nexus.admin.config.AppConfig.{HttpConfig, PaginationConfig}
import ch.epfl.bluebrain.nexus.admin.config.Permissions.{projects => pp}
import ch.epfl.bluebrain.nexus.admin.directives.{AuthDirectives, QueryDirectives}
import ch.epfl.bluebrain.nexus.admin.marshallers.instances._
import ch.epfl.bluebrain.nexus.admin.projects.{ProjectDescription, Projects}
import ch.epfl.bluebrain.nexus.admin.types.ResourceF._
import ch.epfl.bluebrain.nexus.iam.client.IamClient
import ch.epfl.bluebrain.nexus.iam.client.config.IamClientConfig
import ch.epfl.bluebrain.nexus.iam.client.types.AuthToken
import monix.eval.Task
import monix.execution.Scheduler

class ProjectRoutes(projects: Projects[Task])(implicit ic: IamClient[Task],
                                              icc: IamClientConfig,
                                              hc: HttpConfig,
                                              pagination: PaginationConfig,
                                              s: Scheduler)
    extends AuthDirectives(ic)
    with QueryDirectives
    with CombinedRoutes {

  def routes: Route = combinedRoutesFor("projects")

  override def combined(implicit credentials: Option[AuthToken]): Route =
    readRoutes ~ writeRoutes

  private def readRoutes(implicit credentials: Option[AuthToken]): Route =
    extractResourcePath { path =>
      (get & authorizeOn(path, pp.read)) {
        optionalOrg(path) {
          case Some(org) =>
            (trace("listOrganizationProjects") & paginated) { pagination =>
              complete(projects.list(org, pagination).runToFuture)
            }
          case None =>
            (trace("listAllProjects") & paginated) { pagination =>
              complete(projects.list(pagination).runToFuture)
            }
        } ~ extractProject(path) {
          case (org, label) =>
            parameter('rev.as[Long].?) {
              case Some(rev) =>
                trace("getProjectRev") {
                  complete(projects.fetch(org, label, rev).runToFuture)
                }
              case None =>
                trace("getProject") {
                  onSuccess(projects.fetch(org, label).runToFuture) {
                    case Some(res) => complete(res)
                    case None      => complete(StatusCodes.NotFound)
                  }
                }
            }
        }
      }
    }

  private def writeRoutes(implicit credentials: Option[AuthToken]): Route =
    extractResourcePath { path =>
      (put & entity(as[ProjectDescription])) { proj =>
        authCaller.apply { implicit caller =>
          parameter('rev.as[Long].?) {
            case Some(rev) =>
              (trace("updateProject") & authorizeOn(path, pp.write)) {
                extractProject(path) {
                  case (org, label) =>
                    complete(
                      projects
                        .update(org, label, proj, rev)
                        .runToFuture)
                }
              }
            case None =>
              (trace("createProject") & authorizeOn(path, pp.create)) {
                extractProject(path) {
                  case (org, label) =>
                    onSuccess(
                      projects
                        .create(org, label, proj)
                        .runToFuture) {
                      case Right(meta)     => complete(StatusCodes.Created -> meta)
                      case Left(rejection) => complete(rejection)
                    }
                }
              }
          }
        }
      } ~
        delete {
          parameter('rev.as[Long]) { rev =>
            authCaller.apply { implicit caller =>
              (trace("deprecateProject") & authorizeOn(path, pp.write)) {
                extractProject(path) {
                  case (org, label) =>
                    complete(projects.deprecate(org, label, rev).runToFuture)
                }
              }
            }
          }
        }
    }

}

object ProjectRoutes {
  def apply(projects: Projects[Task])(
      implicit ic: IamClient[Task],
      icc: IamClientConfig,
      hc: HttpConfig,
      pagination: PaginationConfig,
      s: Scheduler
  ): ProjectRoutes =
    new ProjectRoutes(projects)
}

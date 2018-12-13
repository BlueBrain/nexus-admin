package ch.epfl.bluebrain.nexus.admin.routes

import akka.http.javadsl.server.Rejections.validationRejection
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{Directive0, Route}
import ch.epfl.bluebrain.nexus.admin.config.AppConfig.PaginationConfig
import ch.epfl.bluebrain.nexus.admin.config.AppConfig.tracing.trace
import ch.epfl.bluebrain.nexus.admin.directives.{AuthDirectives, QueryDirectives}
import ch.epfl.bluebrain.nexus.admin.marshallers.instances._
import ch.epfl.bluebrain.nexus.admin.projects.{Project, Projects}
import ch.epfl.bluebrain.nexus.admin.types.ResourceF._
import ch.epfl.bluebrain.nexus.iam.client.IamClient
import ch.epfl.bluebrain.nexus.iam.client.config.IamClientConfig
import ch.epfl.bluebrain.nexus.iam.client.types.{AuthToken, Permission}
import monix.eval.Task
import monix.execution.Scheduler

class ProjectRoutes(projects: Projects[Task])(implicit iamClient: IamClient[Task],
                                              iamClientConfig: IamClientConfig,
                                              pagination: PaginationConfig,
                                              s: Scheduler)
    extends AuthDirectives(iamClient)
    with QueryDirectives
    with CombinedRoutes {

  private val read  = Permission.unsafe("projects/read")
  private val write = Permission.unsafe("projects/write")

  def routes: Route = combinedRoutesFor("projects")

  override def combined(implicit credentials: Option[AuthToken]): Route =
    readRoutes ~ writeRoutes

  private def readRoutes(implicit credentials: Option[AuthToken]): Route =
    extractResourcePath { path =>
      (get & authorizeOn(path, read)) {
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
      (put & entity(as[Project])) { project =>
        authCaller.apply { implicit caller =>
          parameter('rev.as[Long].?) {
            case Some(rev) =>
              (trace("updateProject") & authorizeOn(path, write)) {
                extractProject(path) {
                  case (org, label) =>
                    isValid(project, org, label) {
                      complete(projects.update(project, rev).runToFuture)
                    }
                }
              }
            case None =>
              (trace("createProject") & authorizeOn(path, write)) {
                extractProject(path) {
                  case (org, label) =>
                    isValid(project, org, label) {
                      onSuccess(projects.create(project).runToFuture) {
                        case Right(meta)     => complete(StatusCodes.Created -> meta)
                        case Left(rejection) => complete(rejection)
                      }
                    }
                }
              }
          }
        }
      } ~
        delete {
          parameter('rev.as[Long]) { rev =>
            authCaller.apply { implicit caller =>
              (trace("deprecateProject") & authorizeOn(path, write)) {
                extractProject(path) {
                  case (org, label) =>
                    complete(projects.deprecate(org, label, rev).runToFuture)
                }
              }
            }
          }
        }
    }

  private def isValid(project: Project, organization: String, label: String): Directive0 = {
    if (project.organization != organization)
      reject(validationRejection(s"Resource path doesn't match provided organization: '$organization'"))
    else if (project.label != label)
      reject(validationRejection(s"Resource path doesn't match provided project: '$label'"))
    else
      pass
  }
}

object ProjectRoutes {
  def apply(projects: Projects[Task])(implicit iamClient: IamClient[Task],
                                      iamClientConfig: IamClientConfig,
                                      pagination: PaginationConfig,
                                      s: Scheduler): ProjectRoutes =
    new ProjectRoutes(projects)
}

package ch.epfl.bluebrain.nexus.admin.routes

import akka.http.javadsl.server.Rejections.validationRejection
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{Directive0, Route}
import ch.epfl.bluebrain.nexus.admin.config.AppConfig.tracing.trace
import ch.epfl.bluebrain.nexus.admin.directives.{AuthDirectives, QueryDirectives}
import ch.epfl.bluebrain.nexus.admin.marshallers.instances._
import ch.epfl.bluebrain.nexus.admin.organizations.{Organization, Organizations}
import ch.epfl.bluebrain.nexus.admin.types.ResourceF.resourceMetaEncoder
import ch.epfl.bluebrain.nexus.iam.client.IamClient
import ch.epfl.bluebrain.nexus.iam.client.types.AuthToken
import monix.eval.Task
import monix.execution.Scheduler

class OrganizationRoutes(organizations: Organizations[Task])(implicit iamClient: IamClient[Task], s: Scheduler)
    extends AuthDirectives(iamClient)
    with QueryDirectives
    with CombinedRoutes {

  override def combined(implicit credentials: Option[AuthToken]): Route =
    readRoutes ~ writeRoutes

  private def readRoutes(implicit credentials: Option[AuthToken]): Route =
    (extractResourcePath & pathEndOrSingleSlash) { resource =>
      (get & authorizeOn(resource)) { path =>
        extractOrg(path) { name =>
          parameter('rev.as[Long].?) {
            case Some(rev) =>
              trace("getOrgRev") {
                complete(organizations.fetch(name, rev).runToFuture)
              }
            case None =>
              trace("getOrg") {
                complete(organizations.fetch(name).runToFuture)
              }
          }
        }
      }
    }

  private def writeRoutes(implicit credentials: Option[AuthToken]): Route =
    (extractResourcePath & pathEndOrSingleSlash) { resource =>
      (put & entity(as[Organization])) { org =>
        authCaller.apply { implicit caller =>
          parameter('rev.as[Long].?) {
            case Some(rev) =>
              (trace("updateProject") & authorizeOn(resource)) { path =>
                extractOrg(path) { label =>
                  isValid(org, label) {
                    complete(organizations.update(label, org, rev).runToFuture)
                  }
                }
              }
            case None =>
              (trace("createProject") & authorizeOn(resource)) { path =>
                extractOrg(path) { label =>
                  isValid(org, label) {
                    complete(StatusCodes.Created -> organizations.create(org).runToFuture)
                  }
                }
              }
          }
        }
      } ~
        delete {
          parameter('rev.as[Long]) { rev =>
            authCaller.apply { implicit caller =>
              (trace("deprecateProject") & authorizeOn(resource)) { path =>
                extractOrg(path) { name =>
                  complete(organizations.deprecate(name, rev).runToFuture)
                }
              }
            }
          }
        }
    }

  private def isValid(organization: Organization, label: String): Directive0 = {
    if (organization.label != label)
      reject(validationRejection(s"Resource path doesn't match provided organization: '$label'"))
    else pass
  }
}

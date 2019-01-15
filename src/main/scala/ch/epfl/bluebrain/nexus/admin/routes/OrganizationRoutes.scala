package ch.epfl.bluebrain.nexus.admin.routes

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import ch.epfl.bluebrain.nexus.admin.config.AppConfig.{HttpConfig, PaginationConfig}
import ch.epfl.bluebrain.nexus.admin.config.AppConfig.tracing.trace
import ch.epfl.bluebrain.nexus.admin.config.Permissions.orgs
import ch.epfl.bluebrain.nexus.admin.directives.{AuthDirectives, QueryDirectives}
import ch.epfl.bluebrain.nexus.admin.marshallers.instances._
import ch.epfl.bluebrain.nexus.admin.organizations.{Organization, OrganizationDescription, Organizations}
import ch.epfl.bluebrain.nexus.admin.types.ResourceF._
import ch.epfl.bluebrain.nexus.iam.client.IamClient
import ch.epfl.bluebrain.nexus.iam.client.config.IamClientConfig
import ch.epfl.bluebrain.nexus.iam.client.types.AuthToken
import ch.epfl.bluebrain.nexus.rdf.Iri.Path
import monix.eval.Task
import monix.execution.Scheduler

class OrganizationRoutes(organizations: Organizations[Task])(implicit ic: IamClient[Task],
                                                             icc: IamClientConfig,
                                                             hc: HttpConfig,
                                                             pagination: PaginationConfig,
                                                             s: Scheduler)
    extends AuthDirectives(ic)
    with QueryDirectives
    with CombinedRoutes {

  def routes: Route = combinedRoutesFor("orgs")

  override def combined(implicit credentials: Option[AuthToken]): Route =
    listRoutes ~ readRoutes ~ writeRoutes

  private def readRoutes(implicit credentials: Option[AuthToken]): Route =
    extractResourcePath { path =>
      (get & authorizeOn(path, orgs.read)) {
        extractOrg(path) { name =>
          parameter('rev.as[Long].?) { rev =>
            trace("getOrganization") {
              onSuccess(organizations.fetch(name, rev).runToFuture) {
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
      (put & entity(as[OrganizationDescription])) { org =>
        authCaller.apply { implicit caller =>
          parameter('rev.as[Long].?) {
            case Some(rev) =>
              (trace("updateOrganization") & authorizeOn(path, orgs.write)) {
                extractOrg(path) { label =>
                  complete(organizations.update(label, Organization(label, org.description), rev).runToFuture)
                }
              }
            case None =>
              (trace("createOrganization") & authorizeOn(path, orgs.create)) {
                extractOrg(path) { label =>
                  onSuccess(organizations.create(Organization(label, org.description)).runToFuture) {
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
              (trace("deprecateOrganization") & authorizeOn(path, orgs.write)) {
                extractOrg(path) { name =>
                  complete(organizations.deprecate(name, rev).runToFuture)
                }
              }
            }
          }
        }
    }

  private def listRoutes(implicit credentials: Option[AuthToken]): Route =
    (pathEndOrSingleSlash & get & paginated) { pagination =>
      (trace("listOrganizations") & authorizeOn(Path./, orgs.read)) {
        complete(organizations.list(pagination).runToFuture)
      }
    }

}

object OrganizationRoutes {
  def apply(organizations: Organizations[Task])(
      implicit ic: IamClient[Task],
      icc: IamClientConfig,
      hc: HttpConfig,
      pagination: PaginationConfig,
      s: Scheduler
  ): OrganizationRoutes =
    new OrganizationRoutes(organizations)
}

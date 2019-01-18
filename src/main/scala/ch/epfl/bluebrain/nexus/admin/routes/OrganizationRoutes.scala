package ch.epfl.bluebrain.nexus.admin.routes

import akka.http.scaladsl.model.StatusCodes.Created
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import ch.epfl.bluebrain.nexus.admin.config.AppConfig.PaginationConfig
import ch.epfl.bluebrain.nexus.admin.config.AppConfig.tracing.trace
import ch.epfl.bluebrain.nexus.admin.config.Permissions.orgs
import ch.epfl.bluebrain.nexus.admin.directives.{AuthDirectives, QueryDirectives}
import ch.epfl.bluebrain.nexus.admin.marshallers.instances._
import ch.epfl.bluebrain.nexus.admin.organizations.{Organization, OrganizationDescription, Organizations}
import ch.epfl.bluebrain.nexus.admin.types.ResourceF._
import ch.epfl.bluebrain.nexus.iam.client.IamClient
import ch.epfl.bluebrain.nexus.iam.client.config.IamClientConfig
import ch.epfl.bluebrain.nexus.rdf.Iri.Path
import monix.eval.Task
import monix.execution.Scheduler

class OrganizationRoutes(organizations: Organizations[Task])(
    implicit ic: IamClient[Task],
    icc: IamClientConfig,
    pc: PaginationConfig,
    s: Scheduler
) extends AuthDirectives(ic)
    with QueryDirectives {

  def routes: Route = (pathPrefix("orgs") & extractToken) { implicit token =>
    concat(
      // listing
      (get & pathEndOrSingleSlash & paginated & authorizeOn(Path./, orgs.read)) { pagination =>
        trace("listOrganizations") {
          complete(organizations.list(pagination).runToFuture)
        }
      },
      // fetch
      (get & pathPrefix(Segment) & pathEndOrSingleSlash & parameter('rev.as[Long].?)) { (orgLabel, optRev) =>
        authorizeOn(pathOf(orgLabel), orgs.read).apply {
          trace("fetchOrganization") {
            complete(organizations.fetch(orgLabel, optRev).runNotFound)
          }
        }
      },
      // writes
      (pathPrefix(Segment) & pathEndOrSingleSlash) { orgLabel =>
        extractSubject.apply {
          implicit subject =>
            concat(
              // deprecate
              (delete & parameter('rev.as[Long]) & authorizeOn(pathOf(orgLabel), orgs.write)) { rev =>
                trace("deprecateOrganization") {
                  complete(organizations.deprecate(orgLabel, rev).runToFuture)
                }
              },
              // update
              (put & parameter('rev.as[Long]) & authorizeOn(pathOf(orgLabel), orgs.write)) { rev =>
                entity(as[OrganizationDescription]) { org =>
                  trace("updateOrganization") {
                    complete(organizations.update(orgLabel, Organization(orgLabel, org.description), rev).runToFuture)
                  }
                }
              },
              // create
              (put & authorizeOn(pathOf(orgLabel), orgs.create)) {
                entity(as[OrganizationDescription]) { org =>
                  trace("createOrganization") {
                    complete(organizations.create(Organization(orgLabel, org.description)).runWithStatus(Created))
                  }
                }
              }
            )
        }
      }
    )
  }

  private def pathOf(orgLabel: String): Path = {
    import ch.epfl.bluebrain.nexus.rdf.Iri.Path._
    Segment(orgLabel, Path./)
  }
}

object OrganizationRoutes {
  def apply(organizations: Organizations[Task])(
      implicit ic: IamClient[Task],
      icc: IamClientConfig,
      pagination: PaginationConfig,
      s: Scheduler
  ): OrganizationRoutes =
    new OrganizationRoutes(organizations)
}

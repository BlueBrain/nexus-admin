package ch.epfl.bluebrain.nexus.admin.routes

import akka.http.scaladsl.model.StatusCodes.Created
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import ch.epfl.bluebrain.nexus.admin.config.AppConfig.PaginationConfig
import ch.epfl.bluebrain.nexus.admin.config.AppConfig.tracing.trace
import ch.epfl.bluebrain.nexus.admin.config.Permissions.{projects => pp}
import ch.epfl.bluebrain.nexus.admin.directives.{AuthDirectives, QueryDirectives}
import ch.epfl.bluebrain.nexus.admin.directives.PathDirectives._
import ch.epfl.bluebrain.nexus.admin.index.{OrganizationCache, ProjectCache}
import ch.epfl.bluebrain.nexus.admin.marshallers.instances._
import ch.epfl.bluebrain.nexus.admin.projects.{ProjectDescription, Projects}
import ch.epfl.bluebrain.nexus.admin.routes.SearchParams.Field
import ch.epfl.bluebrain.nexus.admin.types.ResourceF._
import ch.epfl.bluebrain.nexus.iam.client.IamClient
import ch.epfl.bluebrain.nexus.iam.client.config.IamClientConfig
import ch.epfl.bluebrain.nexus.rdf.Iri.Path
import monix.eval.Task
import monix.execution.Scheduler

class ProjectRoutes(projects: Projects[Task])(
    implicit ic: IamClient[Task],
    orgCache: OrganizationCache[Task],
    projCache: ProjectCache[Task],
    icc: IamClientConfig,
    pagination: PaginationConfig,
    s: Scheduler
) extends AuthDirectives(ic)
    with QueryDirectives {

  def routes: Route = (pathPrefix("projects") & extractToken) { implicit token =>
    concat(
      // fetch
      (get & project & pathEndOrSingleSlash) {
        case (orgLabel, projectLabel) =>
          authorizeOn(pathOf(orgLabel, projectLabel), pp.read).apply {
            parameter('rev.as[Long].?) {
              case Some(rev) =>
                trace("getProjectRev") {
                  complete(projects.fetch(orgLabel, projectLabel, rev).runToFuture)

                }
              case None =>
                trace("getProject") {
                  complete(projects.fetch(orgLabel, projectLabel).runNotFound)

                }
            }
          }
      },
      // writes
      extractSubject.apply { implicit subject =>
        concat(
          (project & pathEndOrSingleSlash) {
            case (orgLabel, projectLabel) =>
              concat(
                // deprecate
                (delete & parameter('rev.as[Long]) & authorizeOn(pathOf(orgLabel, projectLabel), pp.write)) { rev =>
                  trace("deprecateProject") {
                    complete(projects.deprecate(orgLabel, projectLabel, rev).runToFuture)
                  }
                },
                // update
                (put & parameter('rev.as[Long]) & authorizeOn(pathOf(orgLabel, projectLabel), pp.write)) { rev =>
                  entity(as[ProjectDescription]) { project =>
                    trace("updateProject") {
                      complete(projects.update(orgLabel, projectLabel, project, rev).runToFuture)
                    }
                  }
                }
              )
          },
          // create
          (pathPrefix(Segment / Segment) & pathEndOrSingleSlash) { (orgLabel, projectLabel) =>
            (put & authorizeOn(pathOf(orgLabel), pp.create)) {
              entity(as[ProjectDescription]) { project =>
                trace("createProject") {
                  complete(projects.create(orgLabel, projectLabel, project).runWithStatus(Created))
                }
              }
            }
          }
        )

      },
      // list all projects
      (get & pathEndOrSingleSlash & paginated & searchParamsProjects & extractCallerAcls(anyProject)) {
        (pagination, params, acls) =>
          trace("listAllProjects") {
            complete(projects.list(params, pagination)(acls).runToFuture)
          }
      },
      // list projects in organization
      (get & org & pathEndOrSingleSlash & paginated & searchParamsProjects & extractCallerAcls(anyProject)) {
        (orgLabel, pagination, params, acls) =>
          trace("listProjectsInOrganization") {
            val orgField = Some(Field(orgLabel, exactMatch = true))
            complete(projects.list(params.copy(organizationLabel = orgField), pagination)(acls).runToFuture)
          }
      }
    )
  }

  private def pathOf(orgLabel: String): Path = {
    import ch.epfl.bluebrain.nexus.rdf.Iri.Path._
    Segment(orgLabel, Path./)
  }

  private def pathOf(orgLabel: String, projectLabel: String): Path = {
    import ch.epfl.bluebrain.nexus.rdf.Iri.Path._
    orgLabel / projectLabel
  }
}

object ProjectRoutes {
  def apply(projects: Projects[Task])(
      implicit ic: IamClient[Task],
      orgCache: OrganizationCache[Task],
      projCache: ProjectCache[Task],
      icc: IamClientConfig,
      pagination: PaginationConfig,
      s: Scheduler
  ): ProjectRoutes =
    new ProjectRoutes(projects)
}

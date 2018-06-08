package ch.epfl.bluebrain.nexus.admin.service.routes

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.Uri.Path
import akka.http.scaladsl.model.headers.OAuth2BearerToken
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import ch.epfl.bluebrain.nexus.admin.core.CallerCtx._
import ch.epfl.bluebrain.nexus.admin.core.config.AppConfig
import ch.epfl.bluebrain.nexus.admin.core.config.AppConfig._
import ch.epfl.bluebrain.nexus.admin.core.projects.Projects
import ch.epfl.bluebrain.nexus.admin.core.resources.Resource
import ch.epfl.bluebrain.nexus.admin.core.types.Ref
import ch.epfl.bluebrain.nexus.admin.core.types.Ref._
import ch.epfl.bluebrain.nexus.admin.core.types.RefVersioned._
import ch.epfl.bluebrain.nexus.admin.refined.ld.Id
import ch.epfl.bluebrain.nexus.admin.refined.permissions.{HasCreateProjects, HasReadProjects, HasWriteProjects}
import ch.epfl.bluebrain.nexus.admin.refined.project.ProjectReference
import ch.epfl.bluebrain.nexus.admin.refined.project.ProjectUri._
import ch.epfl.bluebrain.nexus.admin.service.directives.AuthDirectives._
import ch.epfl.bluebrain.nexus.admin.service.directives.QueryDirectives._
import ch.epfl.bluebrain.nexus.admin.service.directives.RefinedDirectives._
import ch.epfl.bluebrain.nexus.admin.service.encoders.project._
import ch.epfl.bluebrain.nexus.admin.service.encoders.RoutesEncoder
import ch.epfl.bluebrain.nexus.admin.service.routes.SearchResponse._
import ch.epfl.bluebrain.nexus.commons.http.JsonLdCirceSupport.{jsonUnmarshaller, marshallerHttp}
import ch.epfl.bluebrain.nexus.iam.client.IamClient
import ch.epfl.bluebrain.nexus.service.kamon.directives.TracingDirectives
import io.circe.Json

import scala.concurrent.{ExecutionContext, Future}

/**
  * Http route definitions for domain specific functionality.
  *
  * @param projects               the domain operation bundle
  */
final class ProjectRoutes(projects: Projects[Future])(implicit iamClient: IamClient[Future],
                                                      ec: ExecutionContext,
                                                      config: AppConfig,
                                                      tracing: TracingDirectives)
    extends BaseRoute {

  import tracing._
  implicit val pc: PaginationConfig = config.pagination

  implicit val projectNamespace = config.projects.namespace
  val idResolvable              = Ref.projectRefToResolvable
  implicit val idExtractor: Resource[ProjectReference] => Id = { p =>
    idResolvable(p.id.value)
  }

  private implicit val encoders: RoutesEncoder[Resource[ProjectReference]] =
    new RoutesEncoder[Resource[ProjectReference]]()
  import encoders._
  private def readRoutes(implicit credentials: Option[OAuth2BearerToken]): Route =
    (segment2(of[ProjectReference]) & pathEndOrSingleSlash) { name =>
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

  private def writeRoutes(implicit credentials: Option[OAuth2BearerToken]): Route =
    (segment2(of[ProjectReference]) & pathEndOrSingleSlash) { name =>
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

  override def combined(implicit cred: Option[OAuth2BearerToken]): Route =
    readRoutes(cred) ~ writeRoutes(cred) ~ searchRoutes(cred)

  def routes: Route = combinedRoutesFor("projects")

  private def searchRoutes(implicit credentials: Option[OAuth2BearerToken]): Route =
    (pathEndOrSingleSlash & get & paramsToQuery) { (pagination, query) =>
      trace("searchProjects") {
        (pathEndOrSingleSlash & authorizeOn[HasReadProjects](Path.Empty / "*" / "*")) { implicit acls =>
          implicit val projectNamespace = config.projects.namespace
          implicit val projectsResolver: Id => Future[Option[Resource[ProjectReference]]] = { id =>
            {
              id.projectReference match {
                case Some(projId) => projects.fetch(projId)
                case None         => Future.successful(None)
              }
            }

          }

          projects
            .list(query, pagination)
            .buildResponse[Resource[ProjectReference]](query.fields, config.http.publicUri, pagination)
        }
      }
    }

}

object ProjectRoutes {
  final def apply(projects: Projects[Future])(implicit iamClient: IamClient[Future],
                                              ec: ExecutionContext,
                                              config: AppConfig): ProjectRoutes = {
    implicit val tracing = new TracingDirectives()
    new ProjectRoutes(projects)
  }
}

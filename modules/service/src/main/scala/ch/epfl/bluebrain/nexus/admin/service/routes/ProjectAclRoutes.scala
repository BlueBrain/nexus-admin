package ch.epfl.bluebrain.nexus.admin.service.routes

import akka.http.javadsl.server.CustomRejection
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model.headers.OAuth2BearerToken
import akka.http.scaladsl.model.{Uri, HttpRequest => Req}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{Directive0, RequestContext, Route, RouteResult}
import ch.epfl.bluebrain.nexus.admin.core.config.AppConfig
import ch.epfl.bluebrain.nexus.admin.core.config.AppConfig._
import ch.epfl.bluebrain.nexus.admin.core.projects.Projects
import ch.epfl.bluebrain.nexus.admin.refined.permissions.HasReadProjects
import ch.epfl.bluebrain.nexus.admin.refined.project.ProjectReference
import ch.epfl.bluebrain.nexus.admin.service.directives.AuthDirectives._
import ch.epfl.bluebrain.nexus.admin.service.directives.RefinedDirectives._
import ch.epfl.bluebrain.nexus.admin.service.routes.ProjectAclRoutes.ProjectNotFound
import ch.epfl.bluebrain.nexus.commons.iam.IamClient
import ch.epfl.bluebrain.nexus.commons.iam.acls.Path
import ch.epfl.bluebrain.nexus.commons.iam.acls.Path._
import ch.epfl.bluebrain.nexus.service.kamon.directives.TracingDirectives

import scala.concurrent.Future

/**
  * Http route definitions for domain specific functionality.
  *
  * @param projects               the domain operation bundle
  */
final class ProjectAclRoutes(projects: Projects[Future], proxy: Proxy)(implicit iamClient: IamClient[Future],
                                                                       config: AppConfig,
                                                                       tracing: TracingDirectives)
    extends BaseRoute {
  import tracing._
  private val iamUri = config.iam.baseUri

  protected def combined(implicit credentials: Option[OAuth2BearerToken]): Route =
    (segment(of[ProjectReference]) & pathPrefix("acls") & pathEndOrSingleSlash) { name =>
      (exists(name) & parameterMap & extractRequestContext & extractActorSystem) { (params, ctx, as) =>
        implicit val system = as
        implicit val ec     = system.dispatcher
        val req = Req(ctx.request.method,
                      iamUri.append(Path("acls")).withQuery(Query(params)),
                      entity = ctx.request.entity).withCred(credentials)
        proxy(req).flatMap(ctx.complete(_))
      }
    }

  def routes: Route = combinedRoutesFor("projects")

  private implicit class UriSyntax(uri: Uri) {
    def append(path: Path): Uri =
      uri.copy(path = (uri.path: Path) ++ path)
  }

  private def exists(name: ProjectReference)(implicit credentials: Option[OAuth2BearerToken]): Directive0 =
    (trace("fetchProject") & authorizeOn[HasReadProjects](name.value)) flatMap { implicit perms =>
      onSuccess(projects.fetch(name)) flatMap {
        case Some(_) => pass
        case _       => reject(ProjectNotFound)
      }
    }

  private implicit class RequestSyntax(request: Req) {
    def withCred(implicit credentials: Option[OAuth2BearerToken]): Req =
      credentials.map(request.addCredentials).getOrElse(request)
  }

  private implicit def routeResult2Route(f: Future[RouteResult]): Route =
    (_: RequestContext) => f

}

object ProjectAclRoutes {
  final def apply(projects: Projects[Future], proxy: Proxy)(implicit iamClient: IamClient[Future],
                                                            config: AppConfig): ProjectAclRoutes = {
    implicit val tracing = new TracingDirectives()
    new ProjectAclRoutes(projects, proxy)
  }

  /**
    * Signals that the request was rejected because the project was not found.
    *
    */
  final case object ProjectNotFound extends CustomRejection

}

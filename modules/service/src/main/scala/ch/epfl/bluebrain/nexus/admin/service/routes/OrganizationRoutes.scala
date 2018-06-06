package ch.epfl.bluebrain.nexus.admin.service.routes

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.OAuth2BearerToken
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.Directives._
import ch.epfl.bluebrain.nexus.admin.core.CallerCtx._
import ch.epfl.bluebrain.nexus.admin.core.config.AppConfig
import ch.epfl.bluebrain.nexus.admin.core.config.AppConfig._
import ch.epfl.bluebrain.nexus.admin.core.organizations.Organizations
import ch.epfl.bluebrain.nexus.admin.service.encoders.organization._
import ch.epfl.bluebrain.nexus.admin.core.resources.Resource
import ch.epfl.bluebrain.nexus.admin.core.types.Ref
import ch.epfl.bluebrain.nexus.admin.refined.ld.{Id, Namespace}
import ch.epfl.bluebrain.nexus.admin.refined.organization.OrganizationReference
import ch.epfl.bluebrain.nexus.admin.refined.permissions.{
  HasCreateOrganizations,
  HasReadOrganizations,
  HasWriteOrganizations
}
import ch.epfl.bluebrain.nexus.iam.client.IamClient
import ch.epfl.bluebrain.nexus.admin.service.directives.AuthDirectives._
import ch.epfl.bluebrain.nexus.admin.service.directives.RefinedDirectives._
import ch.epfl.bluebrain.nexus.service.kamon.directives.TracingDirectives
import ch.epfl.bluebrain.nexus.commons.http.JsonLdCirceSupport.{jsonUnmarshaller, marshallerHttp}
import io.circe.Json

import scala.concurrent.Future

final class OrganizationRoutes(organizations: Organizations[Future])(implicit iamClient: IamClient[Future],
                                                                     config: AppConfig,
                                                                     tracing: TracingDirectives)
    extends BaseRoute {
  import tracing._
  implicit val pc: PaginationConfig    = config.pagination
  implicit val orgNamespace: Namespace = config.organizations.namespace

  implicit val idResolvable = Ref.organizationRefToResolvable
  implicit val idExtractor: Resource[OrganizationReference] => Id = { r =>
    idResolvable(r.id.value)
  }

  private def readRoutes(implicit credentials: Option[OAuth2BearerToken]): Route =
    (segment(of[OrganizationReference]) & pathEndOrSingleSlash) { name =>
      (get & authorizeOn[HasReadOrganizations](name.value)) { implicit perms =>
        parameter('rev.as[Long].?) {
          case Some(rev) =>
            trace("getOrgRev") {
              onSuccess(organizations.fetch(name, rev)) {
                case Some(org) => complete(StatusCodes.OK -> org)
                case None      => complete(StatusCodes.NotFound)

              }

            }
          case None =>
            trace("getOrg") {
              onSuccess(organizations.fetch(name)) {
                case Some(org) => complete(StatusCodes.OK -> org)
                case None      => complete(StatusCodes.NotFound)
              }
            }
        }

      }

    }

  private def writeRoutes(implicit credentials: Option[OAuth2BearerToken]): Route =
    (segment(of[OrganizationReference]) & pathEndOrSingleSlash) { name =>
      (put & entity(as[Json])) { json =>
        authCaller.apply { implicit caller =>
          parameter('rev.as[Long].?) {
            case Some(rev) =>
              (trace("updateProject") & authorizeOn[HasWriteOrganizations](name.value)) { implicit perms =>
                onSuccess(organizations.update(name, rev, json)) { ref =>
                  complete(StatusCodes.OK -> ref)
                }
              }
            case None =>
              (trace("createProject") & authorizeOn[HasCreateOrganizations](name.value)) { implicit perms =>
                onSuccess(organizations.create(name, json)) { ref =>
                  complete(StatusCodes.Created -> ref)
                }
              }
          }
        }
      } ~
        delete {
          parameter('rev.as[Long]) { rev =>
            authCaller.apply { implicit caller =>
              (trace("deprecateProject") & authorizeOn[HasWriteOrganizations](name.value)) { implicit perms =>
                onSuccess(organizations.deprecate(name, rev)) { ref =>
                  complete(StatusCodes.OK -> ref)
                }
              }
            }
          }
        }
    }

  override protected def combined(implicit credentials: Option[OAuth2BearerToken]): Route =
    readRoutes(credentials) ~ writeRoutes(credentials)

  def routes: Route = combinedRoutesFor("orgs")

}

object OrganizationRoutes {
  final def apply(organizations: Organizations[Future])(implicit iamClient: IamClient[Future],
                                                        config: AppConfig): OrganizationRoutes = {
    implicit val tracing = new TracingDirectives()

    new OrganizationRoutes(organizations)
  }
}

package ch.epfl.bluebrain.nexus.admin.routes

import akka.actor.ActorSystem
import akka.cluster.Cluster
import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model.headers.Location
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{ExceptionHandler, RejectionHandler, Route}
import ch.epfl.bluebrain.nexus.admin.config.AppConfig
import ch.epfl.bluebrain.nexus.admin.config.AppConfig.{HttpConfig, PaginationConfig, PersistenceConfig}
import ch.epfl.bluebrain.nexus.admin.exceptions.AdminError
import ch.epfl.bluebrain.nexus.admin.exceptions.AdminError.{InternalError, NotFound}
import ch.epfl.bluebrain.nexus.admin.marshallers.instances._
import ch.epfl.bluebrain.nexus.admin.organizations.{OrganizationRejection, Organizations}
import ch.epfl.bluebrain.nexus.admin.projects.{ProjectRejection, Projects}
import ch.epfl.bluebrain.nexus.admin.types.ResourceRejection
import ch.epfl.bluebrain.nexus.iam.client.IamClient
import ch.epfl.bluebrain.nexus.iam.client.config.IamClientConfig
import ch.epfl.bluebrain.nexus.service.http.RejectionHandling
import ch.epfl.bluebrain.nexus.service.http.directives.PrefixDirectives.uriPrefix
import ch.megard.akka.http.cors.scaladsl.CorsDirectives.{cors, corsRejectionHandler}
import ch.megard.akka.http.cors.scaladsl.settings.CorsSettings
import journal.Logger
import monix.eval.Task
import monix.execution.Scheduler.Implicits.global

object Routes {

  private[this] val logger = Logger[this.type]

  /**
    * @return an ExceptionHandler that ensures a descriptive message is returned to the caller
    */
  final val exceptionHandler: ExceptionHandler =
    ExceptionHandler {
      case NotFound =>
        // suppress errors for not found
        complete(AdminError.adminErrorStatusFrom(NotFound) -> (NotFound: AdminError))
      case err =>
        logger.error("Exception caught during routes processing ", err)
        val error: AdminError = InternalError("The system experienced an unexpected error, please try again later.")
        complete(AdminError.adminErrorStatusFrom(error) -> error)
    }

  final val rejectionHandler: RejectionHandler = {
    val custom = RejectionHandling.apply[ResourceRejection]({
      case rejection: OrganizationRejection =>
        logger.debug(s"Handling organization rejection '$rejection'")
        OrganizationRejection.organizationStatusFrom(rejection) -> rejection
      case rejection: ProjectRejection =>
        logger.debug(s"Handling project rejection '$rejection'")
        ProjectRejection.projectStatusFrom(rejection) -> rejection
    })
    corsRejectionHandler withFallback custom withFallback RejectionHandling.notFound withFallback RejectionHandler.default
  }

  final def wrap(route: Route)(implicit hc: HttpConfig): Route = {
    val corsSettings = CorsSettings.defaultSettings
      .withAllowedMethods(List(GET, PUT, POST, DELETE, OPTIONS, HEAD))
      .withExposedHeaders(List(Location.name))
    cors(corsSettings) {
      handleExceptions(exceptionHandler) {
        handleRejections(rejectionHandler) {
          uriPrefix(hc.publicUri) {
            route
          }
        }
      }
    }
  }

  final def apply(
      orgs: Organizations[Task],
      projects: Projects[Task]
  )(implicit as: ActorSystem, cfg: AppConfig, ic: IamClient[Task]): Route = {
    implicit val hc: HttpConfig        = cfg.http
    implicit val pc: PersistenceConfig = cfg.persistence
    implicit val icc: IamClientConfig  = cfg.iam
    implicit val pgc: PaginationConfig = cfg.pagination
    val cluster                        = Cluster(as)

    val eventsRoutes  = EventRoutes().routes
    val orgRoutes     = OrganizationRoutes(orgs).routes
    val projectRoutes = ProjectRoutes(projects).routes
    val infoRoutes = AppInfoRoutes(
      cfg.description,
      ClusterHealthChecker(cluster),
      CassandraHealthChecker(cfg.persistence)
    ).routes

    wrap(
      pathPrefix(cfg.http.prefix) {
        eventsRoutes ~ orgRoutes ~ projectRoutes
      } ~ infoRoutes
    )
  }
}

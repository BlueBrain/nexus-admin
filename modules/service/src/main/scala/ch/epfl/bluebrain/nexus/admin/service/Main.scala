package ch.epfl.bluebrain.nexus.admin.service

import akka.actor.{ActorSystem, AddressFromURIString}
import akka.cluster.Cluster
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.model.headers.Location
import akka.http.scaladsl.server.Directives.handleRejections
import akka.http.scaladsl.server.Route
import akka.stream.{ActorMaterializer, Materializer}
import cats.instances.future._
import ch.epfl.bluebrain.nexus.admin.core.config.AppConfig._
import ch.epfl.bluebrain.nexus.admin.core.config.Settings
import ch.epfl.bluebrain.nexus.admin.core.projects.Projects
import ch.epfl.bluebrain.nexus.admin.core.projects.Projects.EvalProject
import ch.epfl.bluebrain.nexus.admin.core.resources.ResourceState.{Initial, next}
import ch.epfl.bluebrain.nexus.admin.service.routes.ProjectRoutes
import ch.epfl.bluebrain.nexus.commons.http.HttpClient
import ch.epfl.bluebrain.nexus.commons.http.HttpClient.UntypedHttpClient
import ch.epfl.bluebrain.nexus.commons.http.JsonLdCirceSupport._
import ch.epfl.bluebrain.nexus.commons.iam.acls.FullAccessControlList
import ch.epfl.bluebrain.nexus.commons.iam.auth.User
import ch.epfl.bluebrain.nexus.commons.iam.io.serialization.JsonLdSerialization
import ch.epfl.bluebrain.nexus.commons.iam.{IamClient, IamUri}
import ch.epfl.bluebrain.nexus.commons.shacl.validator.{ImportResolver, ShaclSchema, ShaclValidator}
import ch.epfl.bluebrain.nexus.service.http.directives.PrefixDirectives._
import ch.epfl.bluebrain.nexus.sourcing.akka.{ShardingAggregate, SourcingAkkaSettings}
import ch.megard.akka.http.cors.scaladsl.CorsDirectives.{cors, corsRejectionHandler}
import ch.megard.akka.http.cors.scaladsl.settings.CorsSettings
import com.github.jsonldjava.core.DocumentLoader
import com.typesafe.config.ConfigFactory
import io.circe.generic.extras.Configuration
import kamon.Kamon
import kamon.system.SystemMetrics

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Success}

//noinspection TypeAnnotation
// $COVERAGE-OFF$
object Main {

  @SuppressWarnings(Array("UnusedMethodParameter"))
  def main(args: Array[String]): Unit = {
    SystemMetrics.startCollecting()
    Kamon.loadReportersFromConfig()
    val config             = ConfigFactory.load()
    implicit val appConfig = new Settings(config).appConfig

    implicit val as: ActorSystem               = ActorSystem(appConfig.description.actorSystemName, config)
    implicit val ec: ExecutionContext          = as.dispatcher
    implicit val mt: ActorMaterializer         = ActorMaterializer()
    implicit val cl: UntypedHttpClient[Future] = HttpClient.akkaHttpClient
    implicit val iamC: IamClient[Future]       = iamClient(appConfig.iam.baseUri)
    implicit val validator                     = shaclValidator

    val sourcingSettings = SourcingAkkaSettings(journalPluginId = appConfig.persistence.queryJournalPlugin)

    val logger = Logging(as, getClass)

    System.setProperty(DocumentLoader.DISALLOW_REMOTE_CONTEXT_LOADING, "true")

    val baseUri = appConfig.http.publicUri
    val apiUri  = baseUri.copy(path = baseUri.path / appConfig.http.prefix)
    val cluster = Cluster(as)
    cluster.registerOnMemberUp {

      val timeout = appConfig.projects.passivationTimeout
      val agg = ShardingAggregate("project", sourcingSettings.copy(passivationTimeout = timeout))(Initial,
                                                                                                  next,
                                                                                                  EvalProject().apply)
      val projects = Projects(agg)
      val api      = uriPrefix(apiUri)(ProjectRoutes(projects).routes)

      val corsSettings = CorsSettings.defaultSettings
        .copy(allowedMethods = List(GET, PUT, POST, DELETE, OPTIONS, HEAD), exposedHeaders = List(Location.name))

      val routes: Route = handleRejections(corsRejectionHandler)(cors(corsSettings)(api))

      logger.info("==== Cluster is Live ====")

      val httpBinding = {
        Http().bindAndHandle(routes, appConfig.http.interface, appConfig.http.port)
      }

      httpBinding onComplete {
        case Success(binding) =>
          logger.info(s"Bound to ${binding.localAddress.getHostString}: ${binding.localAddress.getPort}")
        case Failure(th) =>
          logger.error(th, "Failed to perform an http binding on {}:{}", appConfig.http.interface, appConfig.http.port)
          Await.result(as.terminate(), 10 seconds)
      }
    }

    val provided = appConfig.cluster.seedAddresses.map(addr =>
      AddressFromURIString(s"akka.tcp://${appConfig.description.actorSystemName}@$addr"))
    val seeds = if (provided.isEmpty) Set(cluster.selfAddress) else provided

    cluster.joinSeedNodes(seeds.toList)

    as.registerOnTermination {
      cluster.leave(cluster.selfAddress)
      Kamon.stopAllReporters()
      SystemMetrics.stopCollecting()
    }
    // attempt to leave the cluster before shutting down
    val _ = sys.addShutdownHook {
      val _ = Await.result(as.terminate(), 15.seconds)
    }
  }

  private def iamClient(baseIamUri: Uri)(implicit ec: ExecutionContext,
                                         mt: Materializer,
                                         cl: UntypedHttpClient[Future]): IamClient[Future] = {
    import io.circe.generic.extras.auto._
    implicit val identityDecoder = JsonLdSerialization.identityDecoder
    implicit val iamUri          = IamUri(baseIamUri)
    implicit val config          = Configuration.default.withDiscriminator("@type")
    implicit val aclCl           = HttpClient.withAkkaUnmarshaller[FullAccessControlList]
    implicit val userCl          = HttpClient.withAkkaUnmarshaller[User]
    IamClient()
  }

  private def shaclValidator(implicit ec: ExecutionContext) = {
    val importResolver: ImportResolver[Future] = (schema: ShaclSchema) => Future(Set.empty)
    ShaclValidator(importResolver)
  }
}
// $COVERAGE-ON$

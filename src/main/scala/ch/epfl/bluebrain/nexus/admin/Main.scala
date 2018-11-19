package ch.epfl.bluebrain.nexus.admin

import java.nio.file.Paths

import akka.actor.{ActorSystem, Address, AddressFromURIString}
import akka.cluster.Cluster
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model.headers.Location
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer
import ch.epfl.bluebrain.nexus.admin.config.Settings
import ch.epfl.bluebrain.nexus.admin.routes._
import ch.epfl.bluebrain.nexus.service.http.routes.StaticResourceRoutes
import ch.megard.akka.http.cors.scaladsl.CorsDirectives.{cors, corsRejectionHandler}
import ch.megard.akka.http.cors.scaladsl.settings.CorsSettings
import com.github.jsonldjava.core.DocumentLoader
import com.typesafe.config.{Config, ConfigFactory}
import kamon.Kamon
import kamon.system.SystemMetrics

import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.duration._
import scala.util.{Failure, Success}

//noinspection TypeAnnotation
// $COVERAGE-OFF$
object Main {

  def loadConfig(): Config = {
    val cfg = sys.env
      .get("ADMIN_CONFIG_FILE")
      .orElse(sys.props.get("admin.config.file"))
      .map { str =>
        val file = Paths.get(str).toAbsolutePath.toFile
        ConfigFactory.parseFile(file)
      }
      .getOrElse(ConfigFactory.empty)

    cfg.withFallback(ConfigFactory.load()).resolve()
  }

  def setupMonitoring(config: Config): Unit = {
    Kamon.reconfigure(config)
    SystemMetrics.startCollecting()
    Kamon.loadReportersFromConfig()
  }

  @SuppressWarnings(Array("UnusedMethodParameter"))
  def main(args: Array[String]): Unit = {
    System.setProperty(DocumentLoader.DISALLOW_REMOTE_CONTEXT_LOADING, "true")
    val config = loadConfig()
    setupMonitoring(config)

    implicit val appConfig             = new Settings(config).appConfig
    implicit val as: ActorSystem       = ActorSystem(appConfig.description.fullName, config)
    implicit val ec: ExecutionContext  = as.dispatcher
    implicit val mt: ActorMaterializer = ActorMaterializer()

    val logger  = Logging(as, getClass)
    val cluster = Cluster(as)
    val seeds: List[Address] = appConfig.cluster.seeds.toList
      .flatMap(_.split(","))
      .map(addr => AddressFromURIString(s"akka.tcp://${appConfig.description.fullName}@$addr")) match {
      case Nil      => List(cluster.selfAddress)
      case nonEmpty => nonEmpty
    }

    val serviceDescription = AppInfoRoutes(appConfig.description,
                                           ClusterHealthChecker(cluster),
                                           CassandraHealthChecker(appConfig.persistence)).routes
    val staticResourceRoutes = new StaticResourceRoutes(
      Map(
        "/contexts/resource"    -> "/resource-context.json",
        "/contexts/filter"      -> "/filter-context.json",
        "/schemas/project"      -> "/schemas/nexus/core/project/v0.1.0.json",
        "/schemas/organization" -> "/schemas/nexus/core/organization/v0.1.0.json"
      ),
      "static",
      appConfig.http.apiUri
    ).routes
    val corsSettings = CorsSettings.defaultSettings
      .withAllowedMethods(List(GET, PUT, POST, DELETE, OPTIONS, HEAD))
      .withExposedHeaders(List(Location.name))
    val routes: Route =
      handleRejections(corsRejectionHandler)(cors(corsSettings)(serviceDescription ~ staticResourceRoutes))

    cluster.registerOnMemberUp {
      logger.info("==== Cluster is Live ====")

      val httpBinding = Http().bindAndHandle(routes, appConfig.http.interface, appConfig.http.port)

      httpBinding.onComplete {
        case Success(binding) =>
          logger.info(s"Bound to ${binding.localAddress.getHostString}: ${binding.localAddress.getPort}")
        case Failure(th) =>
          logger.error(th, "Failed to perform an http binding on {}:{}", appConfig.http.interface, appConfig.http.port)
          Await.result(as.terminate(), 10 seconds)
      }
    }

    cluster.joinSeedNodes(seeds)

    as.registerOnTermination {
      cluster.leave(cluster.selfAddress)
      Kamon.stopAllReporters()
      SystemMetrics.stopCollecting()
    }
    // attempt to leave the cluster before shutting down
    val _ = sys.addShutdownHook {
      val _ = Await.result(as.terminate(), 10.seconds)
    }
  }

}
// $COVERAGE-ON$

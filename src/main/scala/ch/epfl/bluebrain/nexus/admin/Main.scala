package ch.epfl.bluebrain.nexus.admin

import java.nio.file.Paths

import akka.actor.{ActorSystem, Address, AddressFromURIString}
import akka.cluster.Cluster
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer
import ch.epfl.bluebrain.nexus.admin.config.Settings
import ch.epfl.bluebrain.nexus.admin.index._
import ch.epfl.bluebrain.nexus.admin.organizations.Organizations
import ch.epfl.bluebrain.nexus.admin.projects.Projects
import ch.epfl.bluebrain.nexus.admin.routes._
import ch.epfl.bluebrain.nexus.iam.client.IamClient
import com.github.jsonldjava.core.DocumentLoader
import com.typesafe.config.{Config, ConfigFactory}
import kamon.Kamon
import kamon.system.SystemMetrics
import monix.eval.Task
import monix.execution.Scheduler

import scala.concurrent.Await
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
    implicit val storeConfig           = appConfig.keyValueStore
    implicit val as: ActorSystem       = ActorSystem(appConfig.description.fullName, config)
    implicit val mt: ActorMaterializer = ActorMaterializer()
    implicit val scheduler: Scheduler  = Scheduler.global
    implicit val iamConfig             = appConfig.iam
    implicit val iamClient             = IamClient[Task]

    val logger = Logging(as, getClass)

    val cluster = Cluster(as)

    val seeds: List[Address] = appConfig.cluster.seeds.toList
      .flatMap(_.split(","))
      .map(addr => AddressFromURIString(s"akka.tcp://${appConfig.description.fullName}@$addr")) match {
      case Nil      => List(cluster.selfAddress)
      case nonEmpty => nonEmpty
    }
    val orgIndex: OrganizationCache[Task]  = OrganizationCache[Task]
    val projectIndex: ProjectCache[Task]   = ProjectCache[Task]
    val organizations: Organizations[Task] = Organizations[Task](orgIndex, iamClient, appConfig).runSyncUnsafe()
    val projects: Projects[Task]           = Projects(projectIndex, organizations, iamClient, appConfig).runSyncUnsafe()

    cluster.registerOnMemberUp {
      logger.info("==== Cluster is Live ====")
      val routes: Route = Routes(organizations, projects)
      val httpBinding   = Http().bindAndHandle(routes, appConfig.http.interface, appConfig.http.port)

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
    sys.addShutdownHook {
      val _ = Await.result(as.terminate(), 10.seconds)
    }

    OrganizationsIndexer.start(organizations, orgIndex)
    val _ = ProjectsIndexer.start(projects, organizations, projectIndex, orgIndex)
  }
}
// $COVERAGE-ON$

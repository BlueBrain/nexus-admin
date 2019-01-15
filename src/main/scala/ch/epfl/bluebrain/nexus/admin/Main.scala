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
import akka.kafka.ProducerSettings
import akka.stream.ActorMaterializer
import ch.epfl.bluebrain.nexus.admin.config.{AppConfig, Settings}
import ch.epfl.bluebrain.nexus.admin.index._
import ch.epfl.bluebrain.nexus.admin.organizations.{OrganizationEvent, Organizations}
import ch.epfl.bluebrain.nexus.admin.persistence.TaggingAdapter
import ch.epfl.bluebrain.nexus.admin.projects.{ProjectEvent, Projects}
import ch.epfl.bluebrain.nexus.admin.routes._
import ch.epfl.bluebrain.nexus.iam.client.IamClient
import ch.epfl.bluebrain.nexus.service.http.directives.PrefixDirectives.uriPrefix
import ch.epfl.bluebrain.nexus.service.kafka.KafkaPublisher
import ch.megard.akka.http.cors.scaladsl.CorsDirectives.{cors, corsRejectionHandler}
import ch.megard.akka.http.cors.scaladsl.settings.CorsSettings
import com.github.jsonldjava.core.DocumentLoader
import com.typesafe.config.{Config, ConfigFactory}
import kamon.Kamon
import kamon.system.SystemMetrics
import monix.eval.Task
import monix.execution.Scheduler
import org.apache.kafka.common.serialization.StringSerializer

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
    implicit val paginationConfig      = appConfig.pagination

    val logger = Logging(as, getClass)

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
    val orgIndex: OrganizationCache[Task]  = OrganizationCache[Task]
    val projectIndex: ProjectCache[Task]   = ProjectCache[Task]
    val organizations: Organizations[Task] = Organizations[Task](orgIndex, iamClient, appConfig).runSyncUnsafe()
    val projects: Projects[Task]           = Projects(projectIndex, organizations, iamClient, appConfig).runSyncUnsafe()

    val orgRoutes: OrganizationRoutes = OrganizationRoutes(organizations)
    val projectRoutes: ProjectRoutes  = ProjectRoutes(projects)

    val corsSettings = CorsSettings.defaultSettings
      .withAllowedMethods(List(GET, PUT, POST, DELETE, OPTIONS, HEAD))
      .withExposedHeaders(List(Location.name))
    val routes: Route =
      handleRejections(corsRejectionHandler.withFallback(RejectionHandling.notFound))(
        cors(corsSettings)(serviceDescription ~ uriPrefix(appConfig.http.apiUri) {
          orgRoutes.routes ~ projectRoutes.routes
        }))

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
    sys.addShutdownHook {
      val _ = Await.result(as.terminate(), 10.seconds)
    }

    OrganizationsIndexer.start(organizations, orgIndex)
    ProjectsIndexer.start(projects, organizations, projectIndex, orgIndex)

    val _ = startKafkaIndexers(appConfig)

  }

  def startKafkaIndexers(appConfig: AppConfig)(implicit as: ActorSystem) = {
    import ch.epfl.bluebrain.nexus.admin.kafka.encoders._
    import ch.epfl.bluebrain.nexus.admin.kafka.keys._
    implicit val iamClientConfig = appConfig.iam
    val producerSettings         = ProducerSettings(as, new StringSerializer, new StringSerializer)
    KafkaPublisher
      .startTagStream[ProjectEvent](appConfig.persistence.queryJournalPlugin,
                                    TaggingAdapter.ProjectTag,
                                    "projects-to-kafka",
                                    producerSettings,
                                    appConfig.kafka.topic)
    KafkaPublisher
      .startTagStream[OrganizationEvent](appConfig.persistence.queryJournalPlugin,
                                         TaggingAdapter.OrganizationTag,
                                         "orgs-to-kafka",
                                         producerSettings,
                                         appConfig.kafka.topic)
  }

}
// $COVERAGE-ON$

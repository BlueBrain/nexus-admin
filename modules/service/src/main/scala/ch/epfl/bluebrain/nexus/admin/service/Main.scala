package ch.epfl.bluebrain.nexus.admin.service

import akka.actor.{ActorSystem, AddressFromURIString}
import akka.cluster.Cluster
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.Uri
import akka.stream.{ActorMaterializer, Materializer}
import cats.instances.future._
import ch.epfl.bluebrain.nexus.admin.core.config.AppConfig._
import ch.epfl.bluebrain.nexus.admin.core.config.Settings
import ch.epfl.bluebrain.nexus.admin.core.projects.Projects
import ch.epfl.bluebrain.nexus.admin.core.resources.ResourceState.{Initial, eval, next}
import ch.epfl.bluebrain.nexus.admin.service.routes.ProjectRoutes
import ch.epfl.bluebrain.nexus.commons.http.HttpClient
import ch.epfl.bluebrain.nexus.commons.http.HttpClient.UntypedHttpClient
import ch.epfl.bluebrain.nexus.commons.http.JsonLdCirceSupport._
import ch.epfl.bluebrain.nexus.commons.iam.acls.FullAccessControlList
import ch.epfl.bluebrain.nexus.commons.iam.auth.User
import ch.epfl.bluebrain.nexus.commons.iam.io.serialization.JsonLdSerialization
import ch.epfl.bluebrain.nexus.commons.iam.{IamClient, IamUri}
import ch.epfl.bluebrain.nexus.sourcing.akka.{ShardingAggregate, SourcingAkkaSettings}
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

    val sourcingSettings = SourcingAkkaSettings(journalPluginId = appConfig.persistence.queryJournalPlugin)

    val logger = Logging(as, getClass)

    System.setProperty(DocumentLoader.DISALLOW_REMOTE_CONTEXT_LOADING, "true")

    val cluster = Cluster(as)
    cluster.registerOnMemberUp {

      val timeout  = appConfig.projects.passivationTimeout
      val agg      = ShardingAggregate("project", sourcingSettings.copy(passivationTimeout = timeout))(Initial, next, eval)
      val projects = Projects(agg)
      val routes   = ProjectRoutes(projects).routes

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
      SystemMetrics.startCollecting()
    }
    // attempt to leave the cluster before shutting down
    val _ = sys.addShutdownHook {
      val _ = Await.result(as.terminate(), 5.seconds)
    }
  }

  /**
    * Constructs [[IamClient]] from the provided ''baseIamUri'' and the implicitly available instances
    *
    * @param baseIamUri the baseUri for IAM service
    */
  def iamClient(baseIamUri: Uri)(implicit ec: ExecutionContext,
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
}
// $COVERAGE-ON$

package ch.epfl.bluebrain.nexus.admin.service

import java.util.Properties

import akka.actor.{ActorSystem, AddressFromURIString}
import akka.cluster.Cluster
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model.headers.{BasicHttpCredentials, Location}
import akka.http.scaladsl.server.Directives.{handleRejections, _}
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer
import cats.instances.future._
import ch.epfl.bluebrain.nexus.admin.core.config.AppConfig._
import ch.epfl.bluebrain.nexus.admin.core.config.Settings
import ch.epfl.bluebrain.nexus.admin.core.organizations.Organizations
import ch.epfl.bluebrain.nexus.admin.core.projects.Projects
import ch.epfl.bluebrain.nexus.commons.sparql.client.SparqlCirceSupport.sparqlResultsUnmarshaller
import ch.epfl.bluebrain.nexus.admin.core.resources.ResourceEvent
import ch.epfl.bluebrain.nexus.admin.core.resources.ResourceState.{next, Eval, Initial}
import ch.epfl.bluebrain.nexus.admin.service.indexing.ResourceSparqlIndexer
import ch.epfl.bluebrain.nexus.admin.service.routes.Proxy.AkkaStream
import ch.epfl.bluebrain.nexus.admin.service.routes.{OrganizationRoutes, ProjectAclRoutes, ProjectRoutes, StaticRoutes}
import ch.epfl.bluebrain.nexus.commons.http.HttpClient
import ch.epfl.bluebrain.nexus.commons.http.HttpClient._
import ch.epfl.bluebrain.nexus.commons.http.JsonLdCirceSupport._
import ch.epfl.bluebrain.nexus.commons.shacl.validator.{ImportResolver, ShaclValidator}
import ch.epfl.bluebrain.nexus.commons.sparql.client.BlazegraphClient
import ch.epfl.bluebrain.nexus.iam.client.{IamClient, IamUri}
import ch.epfl.bluebrain.nexus.service.http.directives.PrefixDirectives._
import ch.epfl.bluebrain.nexus.service.http.routes.StaticResourceRoutes
import ch.epfl.bluebrain.nexus.service.indexer.persistence.SequentialTagIndexer
import ch.epfl.bluebrain.nexus.sourcing.akka.{ShardingAggregate, SourcingAkkaSettings}
import ch.megard.akka.http.cors.scaladsl.CorsDirectives.{cors, corsRejectionHandler}
import ch.megard.akka.http.cors.scaladsl.settings.CorsSettings
import com.github.jsonldjava.core.DocumentLoader
import com.typesafe.config.ConfigFactory
import io.circe.generic.extras.auto._
import io.circe.{Encoder, Json}
import kamon.Kamon
import kamon.system.SystemMetrics
import org.apache.jena.query.ResultSet

import scala.collection.JavaConverters._
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

    implicit val as: ActorSystem                   = ActorSystem(appConfig.description.actorSystemName, config)
    implicit val ec: ExecutionContext              = as.dispatcher
    implicit val mt: ActorMaterializer             = ActorMaterializer()
    implicit val cl: UntypedHttpClient[Future]     = HttpClient.akkaHttpClient
    implicit val rs: HttpClient[Future, ResultSet] = HttpClient.withAkkaUnmarshaller[ResultSet]
    implicit val iamUri                            = IamUri(appConfig.iam.baseUri)

    implicit val iamC: IamClient[Future] = IamClient()
    implicit val validator               = ShaclValidator(ImportResolver.noop[Future])

    val sourcingSettings = SourcingAkkaSettings(journalPluginId = appConfig.persistence.queryJournalPlugin)

    val logger = Logging(as, getClass)

    System.setProperty(DocumentLoader.DISALLOW_REMOTE_CONTEXT_LOADING, "true")

    val cluster = Cluster(as)
    cluster.registerOnMemberUp {

      val timeout = appConfig.projects.passivationTimeout
      val projAgg =
        ShardingAggregate("project", sourcingSettings.copy(passivationTimeout = timeout))(Initial, next, Eval().apply)
      val orgAgg =
        ShardingAggregate("organizations", sourcingSettings.copy(passivationTimeout = timeout))(Initial,
                                                                                                next,
                                                                                                Eval().apply)
      val blazegraphClient: BlazegraphClient[Future] =
        BlazegraphClient(appConfig.sparql.base,
                         appConfig.sparql.namespace,
                         appConfig.sparql.credentials.map(c => BasicHttpCredentials(c.username, c.password)))

      val organizations: Organizations[Future] = Organizations(orgAgg, blazegraphClient)
      val projects                             = Projects(organizations, projAgg, blazegraphClient)
      val api = uriPrefix(appConfig.http.apiUri)(
        OrganizationRoutes(organizations).routes ~ ProjectRoutes(projects).routes ~ ProjectAclRoutes(
          projects,
          AkkaStream()).routes)
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
      val staticRoutes = StaticRoutes().routes
      val corsSettings = CorsSettings.defaultSettings
        .withAllowedMethods(List(GET, PUT, POST, DELETE, OPTIONS, HEAD))
        .withExposedHeaders(List(Location.name))

      startProjectsIndexer(blazegraphClient, appConfig.persistence)

      val routes: Route =
        handleRejections(corsRejectionHandler)(cors(corsSettings)(staticRoutes ~ staticResourceRoutes ~ api))

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

  private lazy val properties: Map[String, String] = {
    val props = new Properties()
    props.load(getClass.getResourceAsStream("/index.properties"))
    props.asScala.toMap
  }

  private def initFunction(blazegraphClient: BlazegraphClient[Future])(
      implicit ec: ExecutionContext): () => Future[Unit] =
    () =>
      blazegraphClient.namespaceExists.flatMap {
        case true  => Future.successful(())
        case false => blazegraphClient.createNamespace(properties)
    }

  def startProjectsIndexer(blazegraphClient: BlazegraphClient[Future],
                           config: PersistenceConfig)(implicit as: ActorSystem, ec: ExecutionContext): Unit = {
    val indexer      = ResourceSparqlIndexer(blazegraphClient)
    implicit val enc = Encoder.instance[ResourceEvent](_ => Json.obj())
    SequentialTagIndexer.start[ResourceEvent](initFunction(blazegraphClient),
                                              e => indexer.index(e),
                                              "projects-to-3s",
                                              config.queryJournalPlugin,
                                              "project",
                                              "project-to-in-memory")
    ()
  }
}
// $COVERAGE-ON$

package ch.epfl.bluebrain.nexus.admin.service

import java.util.Properties

import akka.actor.{ActorSystem, AddressFromURIString}
import akka.cluster.Cluster
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.model.headers.{BasicHttpCredentials, Location}
import akka.http.scaladsl.server.Directives.{handleRejections, _}
import akka.http.scaladsl.server.Route
import akka.stream.{ActorMaterializer, Materializer}
import cats.instances.future._
import ch.epfl.bluebrain.nexus.admin.core.config.AppConfig._
import ch.epfl.bluebrain.nexus.admin.core.config.Settings
import ch.epfl.bluebrain.nexus.admin.core.projects.Projects
import ch.epfl.bluebrain.nexus.admin.core.projects.Projects.EvalProject
import ch.epfl.bluebrain.nexus.admin.core.resources.ResourceEvent
import ch.epfl.bluebrain.nexus.admin.core.resources.ResourceState.{Initial, next}
import ch.epfl.bluebrain.nexus.admin.service.indexing.ResourceSparqlIndexer
import ch.epfl.bluebrain.nexus.admin.service.routes.Proxy.AkkaStream
import ch.epfl.bluebrain.nexus.admin.service.routes.{ProjectAclRoutes, ProjectRoutes, StaticRoutes}
import ch.epfl.bluebrain.nexus.commons.http.HttpClient
import ch.epfl.bluebrain.nexus.commons.http.HttpClient._
import ch.epfl.bluebrain.nexus.commons.http.JsonLdCirceSupport._
import ch.epfl.bluebrain.nexus.commons.iam.acls.FullAccessControlList
import ch.epfl.bluebrain.nexus.commons.iam.auth.User
import ch.epfl.bluebrain.nexus.commons.iam.io.serialization.JsonLdSerialization
import ch.epfl.bluebrain.nexus.commons.iam.{IamClient, IamUri}
import ch.epfl.bluebrain.nexus.commons.shacl.validator.{ImportResolver, ShaclValidator}
import ch.epfl.bluebrain.nexus.commons.sparql.client.BlazegraphClient
import ch.epfl.bluebrain.nexus.service.http.directives.PrefixDirectives._
import ch.epfl.bluebrain.nexus.service.indexer.persistence.SequentialTagIndexer
import ch.epfl.bluebrain.nexus.sourcing.akka.{ShardingAggregate, SourcingAkkaSettings}
import ch.megard.akka.http.cors.scaladsl.CorsDirectives.{cors, corsRejectionHandler}
import ch.megard.akka.http.cors.scaladsl.settings.CorsSettings
import com.github.jsonldjava.core.DocumentLoader
import com.typesafe.config.ConfigFactory
import io.circe.generic.extras.Configuration
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

    implicit val as: ActorSystem               = ActorSystem(appConfig.description.actorSystemName, config)
    implicit val ec: ExecutionContext          = as.dispatcher
    implicit val mt: ActorMaterializer         = ActorMaterializer()
    implicit val cl: UntypedHttpClient[Future] = HttpClient.akkaHttpClient
    implicit val iamC: IamClient[Future]       = iamClient(appConfig.iam.baseUri)
    implicit val validator                     = ShaclValidator(ImportResolver.noop[Future])

    val sourcingSettings = SourcingAkkaSettings(journalPluginId = appConfig.persistence.queryJournalPlugin)

    val logger = Logging(as, getClass)

    System.setProperty(DocumentLoader.DISALLOW_REMOTE_CONTEXT_LOADING, "true")

    val cluster = Cluster(as)
    cluster.registerOnMemberUp {

      val timeout = appConfig.projects.passivationTimeout
      val agg = ShardingAggregate("project", sourcingSettings.copy(passivationTimeout = timeout))(Initial,
                                                                                                  next,
                                                                                                  EvalProject().apply)
      val blazegraphClient: BlazegraphClient[Future] = sparqlClient(appConfig.sparql)
      val projects                                   = Projects(agg, blazegraphClient)
      val api = uriPrefix(appConfig.http.apiUri)(
        ProjectRoutes(projects).routes ~ ProjectAclRoutes(projects, AkkaStream()).routes)
      val staticRoutes = StaticRoutes().routes
      val corsSettings = CorsSettings.defaultSettings
        .withAllowedMethods(List(GET, PUT, POST, DELETE, OPTIONS, HEAD))
        .withExposedHeaders(List(Location.name))

      startProjectsIndexer(blazegraphClient, appConfig.persistence)

      val routes: Route = handleRejections(corsRejectionHandler)(cors(corsSettings)(staticRoutes ~ api))

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

  /**
    * Constructs [[IamClient]] from the provided ''baseIamUri'' and the implicitly available instances
    *
    * @param baseIamUri the baseUri for IAM service
    */
  private def iamClient(baseIamUri: Uri)(implicit ec: ExecutionContext,
                                         mt: Materializer,
                                         cl: UntypedHttpClient[Future]): IamClient[Future] = {
    implicit val identityDecoder = JsonLdSerialization.identityDecoder
    implicit val iamUri          = IamUri(baseIamUri)
    implicit val config          = Configuration.default.withDiscriminator("@type")
    implicit val aclCl           = HttpClient.withAkkaUnmarshaller[FullAccessControlList]
    implicit val userCl          = HttpClient.withAkkaUnmarshaller[User]
    IamClient()
  }

  def sparqlClient(config: SparqlConfig)(implicit ec: ExecutionContext,
                                         mt: ActorMaterializer,
                                         cl: UntypedHttpClient[Future]): BlazegraphClient[Future] = {
    import ch.epfl.bluebrain.nexus.commons.sparql.client.SparqlCirceSupport.sparqlResultsUnmarshaller
    implicit val rsUnmarshaller = HttpClient.withAkkaUnmarshaller[ResultSet]
    BlazegraphClient(config.base,
                     config.namespace,
                     config.credentials.map(c => BasicHttpCredentials(c.username, c.password)))
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

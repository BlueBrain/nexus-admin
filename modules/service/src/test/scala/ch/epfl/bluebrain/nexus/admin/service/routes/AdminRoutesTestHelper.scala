package ch.epfl.bluebrain.nexus.admin.service.routes

import akka.actor.Props
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.http.scaladsl.model.headers.OAuth2BearerToken
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.util.Timeout
import cats.instances.future._
import ch.epfl.bluebrain.nexus.admin.core.config.{AppConfig, Settings}
import ch.epfl.bluebrain.nexus.admin.core.organizations.Organizations
import ch.epfl.bluebrain.nexus.admin.core.projects.Projects
import ch.epfl.bluebrain.nexus.admin.core.resources.ResourceState.{next, Eval, Initial}
import ch.epfl.bluebrain.nexus.commons.shacl.validator.{ImportResolver, ShaclValidator}
import ch.epfl.bluebrain.nexus.commons.sparql.client.{InMemorySparqlActor, InMemorySparqlClient}
import ch.epfl.bluebrain.nexus.commons.types.identity.Identity.Anonymous
import ch.epfl.bluebrain.nexus.iam.client.Caller.AnonymousCaller
import ch.epfl.bluebrain.nexus.iam.client.IamClient
import ch.epfl.bluebrain.nexus.iam.client.types._
import ch.epfl.bluebrain.nexus.service.http.Path
import ch.epfl.bluebrain.nexus.sourcing.mem.MemoryAggregate
import ch.epfl.bluebrain.nexus.sourcing.mem.MemoryAggregate._
import com.typesafe.config.ConfigFactory
import org.mockito.Mockito.when
import org.scalatest.WordSpecLike
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar

import scala.concurrent.Future
import scala.concurrent.duration._

trait AdminRoutesTestHelper extends WordSpecLike with ScalatestRouteTest with MockitoSugar with ScalaFutures {

  implicit val defaultTimeout                          = Timeout(5 seconds)
  override implicit val patienceConfig: PatienceConfig = PatienceConfig(6 seconds, 300 milliseconds)

  def proxy: Proxy =
    (req: HttpRequest) => Future.successful(HttpResponse(headers = req.headers, entity = req.entity))

  private[routes] val valid                         = ConfigFactory.parseResources("test-app.conf").resolve()
  private[routes] implicit val appConfig: AppConfig = new Settings(valid).appConfig

  private[routes] val caller: AnonymousCaller = AnonymousCaller(Anonymous())
  private[routes] val cred: OAuth2BearerToken = OAuth2BearerToken("validToken")

  private[routes] implicit val optCred: Option[OAuth2BearerToken] = Some(cred)
  private[routes] implicit val cl: IamClient[Future]              = mock[IamClient[Future]]
  private[routes] val acl = FullAccessControlList(
    (Anonymous(),
     Path("projects/proj"),
     Permissions(
       Permission("projects/read"),
       Permission("projects/create"),
       Permission("projects/write"),
       Permission("orgs/read"),
       Permission("orgs/create"),
       Permission("orgs/write")
     )))

  private[routes] val aggProject    = MemoryAggregate("projects")(Initial, next, Eval().apply).toF[Future]
  private[routes] val aggOrg        = MemoryAggregate("orgs")(Initial, next, Eval().apply).toF[Future]
  private val inMemoryActor         = system.actorOf(Props[InMemorySparqlActor]())
  private val sparqlClient          = InMemorySparqlClient(inMemoryActor)
  private[routes] val organizations = Organizations(aggOrg, sparqlClient)
  private[routes] val projects      = Projects(organizations, aggProject, sparqlClient)
  val route: Route

  private[routes] def setUpIamCalls(path: String) = {
    when(cl.getCaller(filterGroups = true)).thenReturn(Future.successful(caller))
    when(cl.getAcls(Path(path), parents = true, self = true)).thenReturn(Future.successful(acl))
  }

  private[routes] implicit def shaclValidator: ShaclValidator[Future] =
    ShaclValidator(ImportResolver.noop[Future])
}

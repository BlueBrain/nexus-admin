package ch.epfl.bluebrain.nexus.admin.service.routes

import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.http.scaladsl.model.headers.{HttpCredentials, OAuth2BearerToken}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.util.Timeout
import cats.instances.future._
import ch.epfl.bluebrain.nexus.admin.core.config.{AppConfig, Settings}
import ch.epfl.bluebrain.nexus.admin.core.organizations.Organizations
import ch.epfl.bluebrain.nexus.admin.core.projects.Projects
import ch.epfl.bluebrain.nexus.admin.core.resources.ResourceState.{next, Eval, Initial}
import ch.epfl.bluebrain.nexus.commons.http.HttpClient
import ch.epfl.bluebrain.nexus.commons.http.HttpClient.UntypedHttpClient
import ch.epfl.bluebrain.nexus.commons.sparql.client.SparqlClient
import ch.epfl.bluebrain.nexus.iam.client.types.Identity._
import ch.epfl.bluebrain.nexus.iam.client.types.Address._
import ch.epfl.bluebrain.nexus.iam.client.Caller.AnonymousCaller
import ch.epfl.bluebrain.nexus.iam.client.IamClient
import ch.epfl.bluebrain.nexus.iam.client.types._
import ch.epfl.bluebrain.nexus.sourcing.mem.MemoryAggregate
import ch.epfl.bluebrain.nexus.sourcing.mem.MemoryAggregate._
import com.typesafe.config.ConfigFactory
import org.apache.jena.query.ResultSet
import org.mockito.Mockito.when
import org.scalatest.WordSpecLike
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import ch.epfl.bluebrain.nexus.commons.sparql.client.SparqlCirceSupport._

import scala.concurrent.Future
import scala.concurrent.duration._

trait AdminRoutesTestHelper extends WordSpecLike with ScalatestRouteTest with MockitoSugar with ScalaFutures {

  implicit val defaultTimeout                          = Timeout(5 seconds)
  override implicit val patienceConfig: PatienceConfig = PatienceConfig(6 seconds, 300 milliseconds)

  private[routes] implicit def toCred(token: AuthToken): HttpCredentials = OAuth2BearerToken(token.value)

  def proxy: Proxy =
    (req: HttpRequest) => Future.successful(HttpResponse(headers = req.headers, entity = req.entity))

  private[routes] val valid                         = ConfigFactory.parseResources("test-app.conf").resolve()
  private[routes] implicit val appConfig: AppConfig = new Settings(valid).appConfig

  private[routes] val caller          = AnonymousCaller
  private[routes] val cred: AuthToken = AuthToken("validToken")

  private[routes] implicit val optCred: Option[AuthToken] = Some(cred)
  private[routes] implicit val cl: IamClient[Future]      = mock[IamClient[Future]]
  private[routes] val acl = FullAccessControlList(
    (Anonymous,
     "projects" / "proj",
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
  private val sparqlClient          = mock[SparqlClient[Future]]
  private[routes] val organizations = Organizations(aggOrg, sparqlClient)
  private[routes] val projects      = Projects(organizations, aggProject, sparqlClient)
  val route: Route

  private[routes] def setUpIamCalls(path: String) = {
    when(cl.getCaller(filterGroups = true)).thenReturn(Future.successful(caller))
    when(cl.getAcls(Address(path), parents = true, self = true)).thenReturn(Future.successful(acl))
  }

  private[routes] implicit val rs: HttpClient[Future, ResultSet] = {
    implicit val ucl: UntypedHttpClient[Future] = HttpClient.akkaHttpClient
    HttpClient.withAkkaUnmarshaller[ResultSet]
  }

}

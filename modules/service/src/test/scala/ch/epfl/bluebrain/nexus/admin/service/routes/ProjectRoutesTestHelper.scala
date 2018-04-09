package ch.epfl.bluebrain.nexus.admin.service.routes

import akka.http.scaladsl.model.headers.OAuth2BearerToken
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.testkit.ScalatestRouteTest
import cats.instances.future._
import ch.epfl.bluebrain.nexus.admin.core.config.{AppConfig, Settings}
import ch.epfl.bluebrain.nexus.admin.core.projects.Projects
import ch.epfl.bluebrain.nexus.admin.core.projects.Projects.EvalProject
import ch.epfl.bluebrain.nexus.admin.core.resources.ResourceState.{Initial, next}
import ch.epfl.bluebrain.nexus.commons.iam.IamClient
import ch.epfl.bluebrain.nexus.commons.iam.acls.{FullAccessControlList, Path, Permission, Permissions}
import ch.epfl.bluebrain.nexus.commons.iam.identity.Caller.AnonymousCaller
import ch.epfl.bluebrain.nexus.commons.iam.identity.Identity.Anonymous
import ch.epfl.bluebrain.nexus.commons.shacl.validator.{ImportResolver, ShaclValidator}
import ch.epfl.bluebrain.nexus.sourcing.mem.MemoryAggregate
import ch.epfl.bluebrain.nexus.sourcing.mem.MemoryAggregate._
import com.typesafe.config.ConfigFactory
import org.mockito.Mockito.when
import org.scalatest.WordSpecLike
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar

import scala.concurrent.Future
import scala.concurrent.duration._

trait ProjectRoutesTestHelper extends WordSpecLike with ScalatestRouteTest with MockitoSugar with ScalaFutures {

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(6 seconds, 300 milliseconds)

  private def proxy: Proxy =
    (req: HttpRequest) => Future.successful(HttpResponse(headers = req.headers, entity = req.entity))

  private[routes] val valid                         = ConfigFactory.parseResources("test-app.conf").resolve()
  private[routes] implicit val appConfig: AppConfig = new Settings(valid).appConfig
//  private[routes] implicit val ec: ExecutionContext         = system.dispatcher

  private[routes] val caller: AnonymousCaller = AnonymousCaller(Anonymous())
  private[routes] val cred: OAuth2BearerToken = OAuth2BearerToken("validToken")

  private[routes] implicit val optCred: Option[OAuth2BearerToken] = Some(cred)
  private[routes] implicit val cl: IamClient[Future]              = mock[IamClient[Future]]
  private[routes] val acl = FullAccessControlList(
    (Anonymous(),
     Path("projects/proj"),
     Permissions(Permission("projects/read"), Permission("projects/create"), Permission("projects/write"))))

  private[routes] val aggProject = MemoryAggregate("projects")(Initial, next, EvalProject().apply).toF[Future]
  private[routes] val projects   = Projects(aggProject)
  private[routes] val route      = ProjectRoutes(projects).routes ~ ProjectAclRoutes(projects, proxy).routes

  private[routes] def setUpIamCalls(path: String) = {
    when(cl.getCaller(optCred, filterGroups = true)).thenReturn(Future.successful(caller))
    when(cl.getAcls(Path(path), parents = true, self = true)).thenReturn(Future.successful(acl))
  }

  private[routes] implicit def shaclValidator: ShaclValidator[Future] =
    ShaclValidator(ImportResolver.noop[Future])
}

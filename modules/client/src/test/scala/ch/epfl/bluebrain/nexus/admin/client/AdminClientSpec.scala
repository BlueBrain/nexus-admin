package ch.epfl.bluebrain.nexus.admin.client

import akka.actor.ActorSystem
import akka.http.scaladsl.model.Uri.Path
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import akka.stream.{ActorMaterializer, Materializer}
import akka.testkit.TestKit
import ch.epfl.bluebrain.nexus.admin.client.config.AdminConfig
import ch.epfl.bluebrain.nexus.admin.client.types.Project.{Config, LoosePrefixMapping}
import ch.epfl.bluebrain.nexus.admin.refined.project.ProjectReference
import ch.epfl.bluebrain.nexus.commons.http.HttpClient.UntypedHttpClient
import ch.epfl.bluebrain.nexus.commons.http.UnexpectedUnsuccessfulHttpResponse
import ch.epfl.bluebrain.nexus.commons.iam.acls.{FullAccessControl, Permission, Permissions}
import ch.epfl.bluebrain.nexus.commons.iam.identity.Identity.UserRef
import ch.epfl.bluebrain.nexus.commons.iam.identity.IdentityId
import ch.epfl.bluebrain.nexus.commons.test.Resources.contentOf
import ch.epfl.bluebrain.nexus.commons.types.HttpRejection.UnauthorizedAccess
import eu.timepit.refined._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

class AdminClientSpec
    extends TestKit(ActorSystem("IamClientSpec"))
    with WordSpecLike
    with Matchers
    with ScalaFutures
    with BeforeAndAfterAll {

  private val base = Uri("http://localhost/v1/projects/")

  private implicit val config: AdminConfig  = AdminConfig(base)
  private implicit val ec: ExecutionContext = system.dispatcher
  private implicit val mt: Materializer     = ActorMaterializer()

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(6.seconds, 300.milliseconds)

  override protected def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
    super.afterAll()
  }

  "An AdminClient" should {

    "return an existing project from upstream" in {
      val name: ProjectReference = refineMV("projectname")
      val mockedResponse = Future.successful(
        HttpResponse(
          entity = HttpEntity(ContentTypes.`application/json`, contentOf("/project.json")),
          status = StatusCodes.OK
        ))
      implicit val cl: UntypedHttpClient[Future] =
        mockedClient(base.copy(path = base.path + "projectname"), Some(OAuth2BearerToken("validToken")), mockedResponse)
      val adminClient = AdminClient(config)

      val project = adminClient.getProject(name, Some(OAuth2BearerToken("validToken"))).futureValue
      project.deprecated shouldEqual false
      project.rev shouldEqual 3
      project.name shouldEqual name
      project.config shouldEqual Config(10)
      project.prefixMappings shouldEqual List(
        LoosePrefixMapping(refineMV("nxv-projectname"),
                           refineMV("https://nexus.example.com/vocabs/nexus/core/terms/v0.1.0/")),
        LoosePrefixMapping(refineMV("person-projectname"), refineMV("https://shapes-registry.org/commons/person"))
      )
    }

    "return ACLs from upstream for an existing project" in {
      val name: ProjectReference = refineMV("projectname")
      val mockedResponse = Future.successful(
        HttpResponse(
          entity = HttpEntity(ContentTypes.`application/json`, contentOf("/project-acls.json")),
          status = StatusCodes.OK
        ))
      implicit val cl: UntypedHttpClient[Future] =
        mockedClient(base.copy(path = base.path + "projectname/acls"),
                     Some(OAuth2BearerToken("validToken")),
                     mockedResponse)
      val adminClient = AdminClient(config)

      val project = adminClient.getProjectAcls(name, Some(OAuth2BearerToken("validToken"))).futureValue
      project.acl shouldEqual List(
        FullAccessControl(
          UserRef(
            IdentityId("https://nexus.example.com/v1/realms/bbp-test/users/ca88f6d1-4f71-4fc0-b023-de82b8afdc30")),
          Path("projectname"),
          Permissions(Permission("projects/read"))
        ))
    }

    "forward unauthorized access errors" in {
      val name: ProjectReference = refineMV("unauthorized")
      val mockedResponse = Future.successful(
        HttpResponse(
          entity = HttpEntity(ContentTypes.`application/json`, contentOf("/unauthorized.json")),
          status = StatusCodes.Unauthorized
        ))
      implicit val cl: UntypedHttpClient[Future] =
        mockedClient(base.copy(path = base.path + "unauthorized"), None, mockedResponse)
      val adminClient = AdminClient(config)

      val error = adminClient.getProject(name, None).failed.futureValue
      error shouldBe a[UnauthorizedAccess.type]
    }

    "handle unexpected upstream errors" in {
      val name: ProjectReference = refineMV("nonexistent")
      val mockedResponse = Future.successful(
        HttpResponse(
          entity = HttpEntity(ContentTypes.`text/plain(UTF-8)`, "Resource not found"),
          status = StatusCodes.NotFound
        ))
      implicit val cl: UntypedHttpClient[Future] =
        mockedClient(base.copy(path = base.path + "nonexistent"), None, mockedResponse)
      val adminClient = AdminClient(config)

      val error = adminClient.getProject(name, None).failed.futureValue
      error shouldBe a[UnexpectedUnsuccessfulHttpResponse]
    }
  }

  def mockedClient(expectedUri: Uri,
                   expectedAuth: Option[OAuth2BearerToken],
                   response: Future[HttpResponse]): UntypedHttpClient[Future] =
    new UntypedHttpClient[Future] {
      override def apply(req: HttpRequest): Future[HttpResponse] =
        if (req.uri == expectedUri) {
          (expectedAuth, req.header[Authorization]) match {
            case (None, None) =>
              response
            case (Some(OAuth2BearerToken(t1)), Some(Authorization(OAuth2BearerToken(t2)))) if t1 == t2 =>
              response
            case _ =>
              fail("Wrong request credentials")
          }
        } else {
          fail(s"Wrong request uri: ${req.uri} expected: $expectedUri")
        }

      override def discardBytes(entity: HttpEntity): Future[HttpMessage.DiscardedEntity] =
        Future.apply(entity.discardBytes())

      override def toString(entity: HttpEntity): Future[String] = Future.apply("")
    }
}

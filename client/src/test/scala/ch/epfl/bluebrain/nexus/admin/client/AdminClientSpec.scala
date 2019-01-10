package ch.epfl.bluebrain.nexus.admin.client

import java.time.Instant
import java.util.UUID

import akka.Done
import akka.actor.ActorSystem
import akka.http.scaladsl.client.RequestBuilding.Get
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.OAuth2BearerToken
import akka.stream.ActorMaterializer
import akka.testkit.TestKit
import cats.effect.IO
import ch.epfl.bluebrain.nexus.admin.client.config.AdminClientConfig
import ch.epfl.bluebrain.nexus.admin.client.types.{Organization, Project}
import ch.epfl.bluebrain.nexus.commons.http.HttpClient.UntypedHttpClient
import ch.epfl.bluebrain.nexus.commons.test.{Randomness, Resources}
import ch.epfl.bluebrain.nexus.commons.test.io.IOOptionValues
import ch.epfl.bluebrain.nexus.commons.types.HttpRejection.UnauthorizedAccess
import ch.epfl.bluebrain.nexus.iam.client.types.AuthToken
import ch.epfl.bluebrain.nexus.rdf.syntax.node.unsafe._
import org.mockito.Mockito
import org.mockito.integrations.scalatest.IdiomaticMockitoFixture
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfter, Matchers, WordSpecLike}

import scala.concurrent.{ExecutionContext, Future}

class AdminClientSpec
    extends TestKit(ActorSystem("AdminClientSpec"))
    with WordSpecLike
    with Matchers
    with ScalaFutures
    with BeforeAndAfter
    with IdiomaticMockitoFixture
    with Randomness
    with IOOptionValues
    with Resources {

  implicit val ec: ExecutionContext  = system.dispatcher
  implicit val mt: ActorMaterializer = ActorMaterializer()

  private val config     = AdminClientConfig(url"http://admin.nexus.example.com/v1".value)
  private val httpClient = mock[UntypedHttpClient[IO]]
  private val client     = new AdminClient[IO](config, httpClient)

  implicit val tokenOpt = Option(AuthToken("token"))
  val token             = OAuth2BearerToken("token")

  val discardedEntity = new HttpMessage.DiscardedEntity(Future.successful(Done))

  before {
    Mockito.reset(httpClient)
    httpClient.discardBytes(any[HttpEntity]) shouldReturn IO.pure(discardedEntity)
  }

  "Admin client" should {

    "fetch organization" in {

      httpClient(Get(s"http://admin.nexus.example.com/v1/orgs/testorg").addCredentials(token)) shouldReturn IO
        .pure(
          HttpResponse(StatusCodes.OK,
                       entity = HttpEntity(ContentTypes.`application/json`, contentOf("/organization.json"))))

      client.fetchOrganization("testorg").some shouldEqual Organization(
        url"http://admin.nexus.example.com/v1/orgs/testorg".value,
        "testorg",
        "Test organization",
        UUID.fromString("504b6940-1b14-43a7-80e3-d08c52c3fc87"),
        1L,
        false,
        Instant.parse("2018-12-19T11:31:30.00Z"),
        url"http://iam.nexus.example.com/v1/realms/example-realm/users/example-user".value,
        Instant.parse("2018-12-20T11:31:30.00Z"),
        url"http://iam.nexus.example.com/v1/realms/example-realm/users/example-user2".value
      )
    }

    "return None if the organization doesn't exist" in {
      val orgLabel = genString()

      httpClient(Get(s"http://admin.nexus.example.com/v1/orgs/$orgLabel").addCredentials(token)) shouldReturn IO
        .pure(HttpResponse(StatusCodes.NotFound, entity = HttpEntity(ContentTypes.`application/json`, "[]")))

      client.fetchOrganization(orgLabel).unsafeRunSync() shouldEqual None
    }
    "fail with UnauthorizedAccess when fetching project" in {
      val orgLabel = genString()

      httpClient(Get(s"http://admin.nexus.example.com/v1/orgs/$orgLabel").addCredentials(token)) shouldReturn IO
        .pure(HttpResponse(StatusCodes.Unauthorized, entity = HttpEntity(ContentTypes.`application/json`, "[]")))

      client.fetchOrganization(orgLabel).unsafeToFuture().failed.futureValue shouldEqual UnauthorizedAccess
    }

    "fail with other error when fetching organization" in {
      val orgLabel  = genString()
      val exception = new RuntimeException()

      httpClient(Get(s"http://admin.nexus.example.com/v1/orgs/$orgLabel").addCredentials(token)) shouldReturn IO {
        throw exception
      }

      client.fetchOrganization(orgLabel).unsafeToFuture().failed.futureValue shouldEqual exception
    }

    "fetch project with optional fields" in {
      httpClient(Get(s"http://admin.nexus.example.com/v1/projects/testorg/testproject").addCredentials(token)) shouldReturn IO
        .pure(
          HttpResponse(StatusCodes.OK,
                       entity = HttpEntity(ContentTypes.`application/json`, contentOf("/project.json"))))

      client.fetchProject("testorg", "testproject").some shouldEqual
        Project(
          url"http://admin.nexus.example.com/v1/projects/testorg/testproject".value,
          "testproject",
          "testorg",
          Some("Test project"),
          url"https://nexus.example.com/base".value,
          url"https://nexus.example.com/voc".value,
          Map(
            "nxv" -> url"https://bluebrain.github.io/nexus/vocabulary/".value,
            "rdf" -> url"http://www.w3.org/1999/02/22-rdf-syntax-ns#".value
          ),
          UUID.fromString("db7cee63-3f93-4d4a-9cc2-ebdace7f3b4f"),
          UUID.fromString("504b6940-1b14-43a7-80e3-d08c52c3fc87"),
          1L,
          deprecated = false,
          Instant.parse("2018-12-19T11:31:30.00Z"),
          url"http://iam.nexus.example.com/v1/realms/example-realm/users/example-user".value,
          Instant.parse("2018-12-20T11:31:30.00Z"),
          url"http://iam.nexus.example.com/v1/realms/example-realm/users/example-user2".value
        )
    }

    "fetch project without optional fields" in {
      httpClient(Get(s"http://admin.nexus.example.com/v1/projects/testorg/testproject").addCredentials(token)) shouldReturn IO
        .pure(
          HttpResponse(StatusCodes.OK,
                       entity = HttpEntity(ContentTypes.`application/json`, contentOf("/minimal-project.json"))))

      client.fetchProject("testorg", "testproject").some shouldEqual
        Project(
          url"http://admin.nexus.example.com/v1/projects/testorg/testproject".value,
          "testproject",
          "testorg",
          None,
          url"https://nexus.example.com/base".value,
          url"https://nexus.example.com/voc".value,
          Map(
            "nxv" -> url"https://bluebrain.github.io/nexus/vocabulary/".value,
            "rdf" -> url"http://www.w3.org/1999/02/22-rdf-syntax-ns#".value
          ),
          UUID.fromString("db7cee63-3f93-4d4a-9cc2-ebdace7f3b4f"),
          UUID.fromString("504b6940-1b14-43a7-80e3-d08c52c3fc87"),
          1L,
          deprecated = false,
          Instant.parse("2018-12-19T11:31:30.00Z"),
          url"http://iam.nexus.example.com/v1/realms/example-realm/users/example-user".value,
          Instant.parse("2018-12-20T11:31:30.00Z"),
          url"http://iam.nexus.example.com/v1/realms/example-realm/users/example-user2".value
        )
    }

    "return None if the project doesn't exist" in {
      val orgLabel     = genString()
      val projectLabel = genString()

      httpClient(Get(s"http://admin.nexus.example.com/v1/projects/$orgLabel/$projectLabel").addCredentials(token)) shouldReturn IO
        .pure(HttpResponse(StatusCodes.NotFound, entity = HttpEntity(ContentTypes.`application/json`, "[]")))

      client.fetchProject(orgLabel, projectLabel).unsafeRunSync() shouldEqual None
    }
    "fail with UnauthorizedAccess when fetching organization" in {
      val orgLabel     = genString()
      val projectLabel = genString()

      httpClient(Get(s"http://admin.nexus.example.com/v1/projects/$orgLabel/$projectLabel").addCredentials(token)) shouldReturn IO
        .pure(HttpResponse(StatusCodes.Unauthorized, entity = HttpEntity(ContentTypes.`application/json`, "[]")))

      client.fetchProject(orgLabel, projectLabel).unsafeToFuture().failed.futureValue shouldEqual UnauthorizedAccess
    }

    "fail with other error when fetching project" in {
      val orgLabel     = genString()
      val projectLabel = genString()
      val exception    = new RuntimeException()

      httpClient(Get(s"http://admin.nexus.example.com/v1/projects/$orgLabel/$projectLabel").addCredentials(token)) shouldReturn IO {
        throw exception
      }

      client.fetchProject(orgLabel, projectLabel).unsafeToFuture().failed.futureValue shouldEqual exception
    }
  }

}

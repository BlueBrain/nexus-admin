package ch.epfl.bluebrain.nexus.admin.client

import java.time.Instant
import java.util.UUID

import akka.actor.ActorSystem
import akka.http.scaladsl.client.RequestBuilding.Get
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.OAuth2BearerToken
import akka.stream.ActorMaterializer
import akka.testkit.TestKit
import cats.effect.IO
import ch.epfl.bluebrain.nexus.admin.client.AdminClientError._
import ch.epfl.bluebrain.nexus.admin.client.config.AdminClientConfig
import ch.epfl.bluebrain.nexus.admin.client.types.{Organization, Project}
import ch.epfl.bluebrain.nexus.commons.http.HttpClient
import ch.epfl.bluebrain.nexus.commons.test.io.IOOptionValues
import ch.epfl.bluebrain.nexus.commons.test.{Randomness, Resources}
import ch.epfl.bluebrain.nexus.iam.client.IamClientError
import ch.epfl.bluebrain.nexus.iam.client.types.AuthToken
import ch.epfl.bluebrain.nexus.rdf.syntax.node.unsafe._
import org.mockito.Mockito
import org.mockito.integrations.scalatest.IdiomaticMockitoFixture
import org.scalatest._
import org.scalatest.concurrent.ScalaFutures
import ch.epfl.bluebrain.nexus.commons.http.syntax.circe._

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

//noinspection NameBooleanParameters
class AdminClientSpec
    extends TestKit(ActorSystem("AdminClientSpec"))
    with WordSpecLike
    with Matchers
    with ScalaFutures
    with BeforeAndAfter
    with IdiomaticMockitoFixture
    with Randomness
    with IOOptionValues
    with EitherValues
    with Inspectors
    with Resources {

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(5 seconds, 15 milliseconds)

  implicit val ec: ExecutionContext  = system.dispatcher
  implicit val mt: ActorMaterializer = ActorMaterializer()

  private val config = AdminClientConfig(
    url"https://nexus.example.com/v1".value,
    url"http://admin.nexus.example.com/v1".value,
  )
  private val token = OAuth2BearerToken("token")

  private implicit val pc: HttpClient[IO, Project]      = mock[HttpClient[IO, Project]]
  private implicit val oc: HttpClient[IO, Organization] = mock[HttpClient[IO, Organization]]
  private implicit val tokenOpt: Option[AuthToken]      = Option(AuthToken("token"))

  private val client = new AdminClient[IO](config)

  private val org       = jsonContentOf("/organization.json").as[Organization].right.value
  private val orgNoDesc = jsonContentOf("/organization.json").removeKeys("description").as[Organization].right.value
  private val proj      = jsonContentOf("/project.json").as[Project].right.value

  private def orgRequest(label: String) =
    Get(s"http://admin.nexus.example.com/v1/orgs/$label").addCredentials(token)

  private def projRequest(org: String, label: String) =
    Get(s"http://admin.nexus.example.com/v1/projects/$org/$label").addCredentials(token)

  before {
    Mockito.reset(pc, oc)
  }

  "The AdminClient" should {

    "fetch organization" in {
      val orgLabel = genString()
      oc(orgRequest(orgLabel)) shouldReturn IO.pure(org)
      client.fetchOrganization(orgLabel).some shouldEqual Organization(
        url"http://admin.nexus.example.com/v1/orgs/testorg".value,
        "testorg",
        Some("Test organization"),
        UUID.fromString("504b6940-1b14-43a7-80e3-d08c52c3fc87"),
        1L,
        false,
        Instant.parse("2018-12-19T11:31:30.00Z"),
        url"http://iam.nexus.example.com/v1/realms/example-realm/users/example-user".value,
        Instant.parse("2018-12-20T11:31:30.00Z"),
        url"http://iam.nexus.example.com/v1/realms/example-realm/users/example-user2".value
      )
    }

    "fetch organization without description" in {
      val orgLabel = genString()
      oc(orgRequest(orgLabel)) shouldReturn IO.pure(orgNoDesc)
      client.fetchOrganization(orgLabel).some shouldEqual Organization(
        url"http://admin.nexus.example.com/v1/orgs/testorg".value,
        "testorg",
        None,
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
      oc(orgRequest(orgLabel)) shouldReturn IO.raiseError(UnknownError(StatusCodes.NotFound, ""))
      client.fetchOrganization(orgLabel).ioValue shouldEqual None
    }

    "propagate the underlying exception when fetching organizations" in {
      val exs: List[Exception] = List(
        IamClientError.Unauthorized(""),
        IamClientError.Forbidden(""),
        AdminClientError.UnmarshallingError[Organization](""),
        AdminClientError.UnknownError(StatusCodes.InternalServerError, "")
      )
      forAll(exs) { ex =>
        val orgLabel = genString()
        oc(orgRequest(orgLabel)) shouldReturn IO.raiseError(ex)
        client.fetchOrganization(orgLabel).failed[Exception] shouldEqual ex
      }
    }

    "fetch a project" in {
      val orgLabel     = genString()
      val projectLabel = genString()
      pc(projRequest(orgLabel, projectLabel)) shouldReturn IO.pure(proj)
      client.fetchProject(orgLabel, projectLabel).some shouldEqual Project(
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

    "return None if the project doesn't exist" in {
      val orgLabel     = genString()
      val projectLabel = genString()
      pc(projRequest(orgLabel, projectLabel)) shouldReturn IO.raiseError(UnknownError(StatusCodes.NotFound, ""))
      client.fetchProject(orgLabel, projectLabel).unsafeRunSync() shouldEqual None
    }

    "propagate the underlying exception when fetching projects" in {
      val exs: List[Exception] = List(
        IamClientError.Unauthorized(""),
        IamClientError.Forbidden(""),
        AdminClientError.UnmarshallingError[Organization](""),
        AdminClientError.UnknownError(StatusCodes.InternalServerError, "")
      )
      forAll(exs) { ex =>
        val orgLabel     = genString()
        val projectLabel = genString()
        pc(projRequest(orgLabel, projectLabel)) shouldReturn IO.raiseError(ex)
        client.fetchProject(orgLabel, projectLabel).failed[Exception] shouldEqual ex
      }
    }
  }

}

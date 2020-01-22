package ch.epfl.bluebrain.nexus.admin.client

import java.time.Instant
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import java.util.regex.Pattern.quote

import akka.actor.ActorSystem
import akka.http.scaladsl.client.RequestBuilding.Get
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.OAuth2BearerToken
import akka.stream.scaladsl.Source
import akka.testkit.TestKit
import cats.effect.IO
import cats.implicits._
import ch.epfl.bluebrain.nexus.admin.client.AdminClientError._
import ch.epfl.bluebrain.nexus.admin.client.config.AdminClientConfig
import ch.epfl.bluebrain.nexus.admin.client.types.events.Event._
import ch.epfl.bluebrain.nexus.admin.client.types.events.{Event, OrganizationEvent, ProjectEvent}
import ch.epfl.bluebrain.nexus.admin.client.types.{Organization, Project, ServiceDescription}
import ch.epfl.bluebrain.nexus.commons.http.HttpClient
import ch.epfl.bluebrain.nexus.commons.search.QueryResult.UnscoredQueryResult
import ch.epfl.bluebrain.nexus.commons.search.QueryResults
import ch.epfl.bluebrain.nexus.commons.search.QueryResults.UnscoredQueryResults
import ch.epfl.bluebrain.nexus.commons.test.io.IOOptionValues
import ch.epfl.bluebrain.nexus.commons.test.{EitherValues, Randomness, Resources}
import ch.epfl.bluebrain.nexus.iam.client.IamClientError
import ch.epfl.bluebrain.nexus.iam.client.types.AuthToken
import ch.epfl.bluebrain.nexus.rdf.Iri
import ch.epfl.bluebrain.nexus.rdf.implicits._
import org.mockito.{IdiomaticMockito, Mockito}
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatest.{BeforeAndAfter, Inspectors}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.Random

//noinspection NameBooleanParameters
class AdminClientSpec
    extends TestKit(ActorSystem("AdminClientSpec"))
    with AnyWordSpecLike
    with Matchers
    with ScalaFutures
    with BeforeAndAfter
    with IdiomaticMockito
    with Randomness
    with IOOptionValues
    with EitherValues
    with Inspectors
    with Resources
    with Eventually {

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(5.seconds, 15.milliseconds)

  implicit val ec: ExecutionContext = system.dispatcher

  private val config = AdminClientConfig(
    url"https://nexus.example.com",
    url"http://admin.nexus.example.com",
    "v1"
  )
  private val token = OAuth2BearerToken("token")

  private implicit val pc: HttpClient[IO, Project]                           = mock[HttpClient[IO, Project]]
  private implicit val pcQr: HttpClient[IO, QueryResults[Project]]           = mock[HttpClient[IO, QueryResults[Project]]]
  private implicit val oc: HttpClient[IO, Organization]                      = mock[HttpClient[IO, Organization]]
  private implicit val serviceDescClient: HttpClient[IO, ServiceDescription] = mock[HttpClient[IO, ServiceDescription]]
  private implicit val tokenOpt: Option[AuthToken]                           = Option(AuthToken("token"))
  private val source                                                         = mock[EventSource[Event]]

  private val client = new AdminClient[IO](source, config)

  private val org       = jsonContentOf("/organization.json").as[Organization].rightValue
  private val orgNoDesc = jsonContentOf("/organization.json").removeKeys("description").as[Organization].rightValue
  private def proj(label: String = "testproject") =
    jsonContentOf("/project.json", Map(quote("{label}") -> label)).as[Project].rightValue

  private def orgRequest(label: String) =
    Get(s"http://admin.nexus.example.com/v1/orgs/$label").addCredentials(token)

  private def projRequest(org: String, label: String) =
    Get(s"http://admin.nexus.example.com/v1/projects/$org/$label").addCredentials(token)

  private def projListRequest(org: String, from: Int, size: Int) =
    Get(s"http://admin.nexus.example.com/v1/projects/$org?from=$from&size=$size").addCredentials(token)

  before {
    Mockito.reset(pc, pcQr, oc, source, serviceDescClient)
  }

  "The AdminClient" should {

    "fetch service description" in {
      val serviceDescription = ServiceDescription("admin", genString())
      serviceDescClient(Get("http://admin.nexus.example.com")) shouldReturn IO.pure(serviceDescription)
      client.serviceDescription.ioValue shouldEqual serviceDescription
    }

    "failed to fetch service description" in {
      val exception = new RuntimeException()
      serviceDescClient(Get("http://admin.nexus.example.com")) shouldReturn IO.raiseError(exception)
      client.serviceDescription.failed[RuntimeException] shouldEqual exception
    }

    "fetch organization" in {
      val orgLabel = genString()
      oc(orgRequest(orgLabel)) shouldReturn IO.pure(org)
      client.fetchOrganization(orgLabel).some shouldEqual Organization(
        url"http://admin.nexus.example.com/v1/orgs/testorg",
        "testorg",
        Some("Test organization"),
        UUID.fromString("504b6940-1b14-43a7-80e3-d08c52c3fc87"),
        1L,
        false,
        Instant.parse("2018-12-19T11:31:30.00Z"),
        url"http://iam.nexus.example.com/v1/realms/example-realm/users/example-user",
        Instant.parse("2018-12-20T11:31:30.00Z"),
        url"http://iam.nexus.example.com/v1/realms/example-realm/users/example-user2"
      )
    }

    "fetch organization without description" in {
      val orgLabel = genString()
      oc(orgRequest(orgLabel)) shouldReturn IO.pure(orgNoDesc)
      client.fetchOrganization(orgLabel).some shouldEqual Organization(
        url"http://admin.nexus.example.com/v1/orgs/testorg",
        "testorg",
        None,
        UUID.fromString("504b6940-1b14-43a7-80e3-d08c52c3fc87"),
        1L,
        false,
        Instant.parse("2018-12-19T11:31:30.00Z"),
        url"http://iam.nexus.example.com/v1/realms/example-realm/users/example-user",
        Instant.parse("2018-12-20T11:31:30.00Z"),
        url"http://iam.nexus.example.com/v1/realms/example-realm/users/example-user2"
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
      pc(projRequest(orgLabel, projectLabel)) shouldReturn IO.pure(proj())
      client.fetchProject(orgLabel, projectLabel).some shouldEqual Project(
        url"http://admin.nexus.example.com/v1/projects/testorg/testproject",
        "testproject",
        "testorg",
        Some("Test project"),
        url"https://nexus.example.com/base",
        url"https://nexus.example.com/voc",
        Map(
          "nxv" -> url"https://bluebrain.github.io/nexus/vocabulary/",
          "rdf" -> url"http://www.w3.org/1999/02/22-rdf-syntax-ns#"
        ),
        UUID.fromString("db7cee63-3f93-4d4a-9cc2-ebdace7f3b4f"),
        UUID.fromString("504b6940-1b14-43a7-80e3-d08c52c3fc87"),
        1L,
        deprecated = false,
        Instant.parse("2018-12-19T11:31:30.00Z"),
        url"http://iam.nexus.example.com/v1/realms/example-realm/users/example-user",
        Instant.parse("2018-12-20T11:31:30.00Z"),
        url"http://iam.nexus.example.com/v1/realms/example-realm/users/example-user2"
      )
    }

    "fetch projects" in {
      val orgLabel  = genString()
      val projects1 = List.tabulate(30)(n => proj(s"label-$n")).map(UnscoredQueryResult(_))
      val projects2 = List.tabulate(30)(n => proj(s"label-${n + 31}")).map(UnscoredQueryResult(_))
      val projects3 = List.tabulate(10)(n => proj(s"label-${n + 61}")).map(UnscoredQueryResult(_))
      pcQr(projListRequest(orgLabel, 0, 30)) shouldReturn IO.pure(UnscoredQueryResults(70L, projects1))
      pcQr(projListRequest(orgLabel, 30, 30)) shouldReturn IO.pure(UnscoredQueryResults(70L, projects2))
      pcQr(projListRequest(orgLabel, 60, 30)) shouldReturn IO.pure(UnscoredQueryResults(70L, projects3))

      val allQueryResults = UnscoredQueryResults(70L, projects1 ++ projects2 ++ projects3)
      client.fetchProjects(orgLabel).ioValue shouldEqual allQueryResults
    }

    "fetch projects returns when project does not exist" in {
      val orgLabel = genString()
      pcQr(projListRequest(orgLabel, 0, 30)) shouldReturn IO.raiseError(UnknownError(StatusCodes.NotFound, ""))
      client.fetchProjects(orgLabel).ioValue shouldEqual UnscoredQueryResults(0L, List.empty)
    }

    "fetch projects empty list" in {
      val orgLabel = genString()
      pcQr(projListRequest(orgLabel, 0, 30)) shouldReturn IO.pure(UnscoredQueryResults(0L, List.empty))
      client.fetchProjects(orgLabel).ioValue shouldEqual UnscoredQueryResults(0L, List.empty)
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

    "reading from the events SSE" should {

      abstract class Ctx {
        val count = new AtomicInteger()
        val resources = List(
          "/events/organization-created.json",
          "/events/organization-updated.json",
          "/events/organization-deprecated.json",
          "/events/project-created.json",
          "/events/project-updated.json",
          "/events/project-deprecated.json"
        )

        val eventsSource = Source(Random.shuffle(resources).map(jsonContentOf(_).as[Event].rightValue))
      }

      "apply function when new organization event is received" in new Ctx {
        val f: OrganizationEvent => IO[Unit] = {
          case _: OrganizationCreated    => IO(count.addAndGet(1)) >> IO.unit
          case _: OrganizationUpdated    => IO(count.addAndGet(2)) >> IO.unit
          case _: OrganizationDeprecated => IO(count.addAndGet(3)) >> IO.unit
        }
        val eventsIri = Iri.url("http://admin.nexus.example.com/v1/orgs/events").rightValue
        source(eventsIri, None) shouldReturn eventsSource
        client.organizationEvents(f)
        eventually(count.get() shouldEqual 6)
      }

      "apply function when new project event is received" in new Ctx {
        val f: ProjectEvent => IO[Unit] = {
          case _: ProjectCreated    => IO(count.addAndGet(1)) >> IO.unit
          case _: ProjectUpdated    => IO(count.addAndGet(2)) >> IO.unit
          case _: ProjectDeprecated => IO(count.addAndGet(3)) >> IO.unit
        }
        val eventsIri = Iri.url("http://admin.nexus.example.com/v1/projects/events").rightValue
        source(eventsIri, None) shouldReturn eventsSource
        client.projectEvents(f)
        eventually(count.get() shouldEqual 6)
      }
    }
  }
}

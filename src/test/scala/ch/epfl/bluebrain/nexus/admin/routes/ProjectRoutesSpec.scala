package ch.epfl.bluebrain.nexus.admin.routes

import java.time.Instant
import java.util.UUID
import java.util.regex.Pattern.quote

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.{BasicHttpCredentials, OAuth2BearerToken}
import akka.http.scaladsl.testkit.ScalatestRouteTest
import ch.epfl.bluebrain.nexus.admin.Error
import ch.epfl.bluebrain.nexus.admin.Error._
import ch.epfl.bluebrain.nexus.admin.config.AppConfig.{HttpConfig, PaginationConfig}
import ch.epfl.bluebrain.nexus.admin.config.Vocabulary.nxv
import ch.epfl.bluebrain.nexus.admin.config.{AppConfig, Settings}
import ch.epfl.bluebrain.nexus.admin.marshallers.instances._
import ch.epfl.bluebrain.nexus.admin.organizations.Organization
import ch.epfl.bluebrain.nexus.admin.projects.ProjectRejection._
import ch.epfl.bluebrain.nexus.admin.projects.{Project, ProjectDescription, Projects}
import ch.epfl.bluebrain.nexus.admin.types.ResourceF
import ch.epfl.bluebrain.nexus.commons.test.Resources
import ch.epfl.bluebrain.nexus.commons.types.search.Pagination
import ch.epfl.bluebrain.nexus.commons.types.search.QueryResult.UnscoredQueryResult
import ch.epfl.bluebrain.nexus.commons.types.search.QueryResults.UnscoredQueryResults
import ch.epfl.bluebrain.nexus.iam.client.IamClient
import ch.epfl.bluebrain.nexus.iam.client.config.IamClientConfig
import ch.epfl.bluebrain.nexus.iam.client.types.{AuthToken, Caller, Identity, Permission}
import ch.epfl.bluebrain.nexus.rdf.Iri
import ch.epfl.bluebrain.nexus.rdf.Iri.Path
import ch.epfl.bluebrain.nexus.rdf.Iri.Path._
import ch.epfl.bluebrain.nexus.rdf.syntax.node.unsafe._
import io.circe.Json
import monix.eval.Task
import monix.execution.Scheduler.global
import org.mockito.integrations.scalatest.IdiomaticMockitoFixture
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{EitherValues, Matchers, WordSpecLike}

//noinspection TypeAnnotation
class ProjectRoutesSpec
    extends WordSpecLike
    with IdiomaticMockitoFixture
    with ScalatestRouteTest
    with ScalaFutures
    with EitherValues
    with Resources
    with Matchers {

  private val iamClient = mock[IamClient[Task]]
  private val projects  = mock[Projects[Task]]

  private val appConfig: AppConfig                      = Settings(system).appConfig
  private implicit val httpConfig: HttpConfig           = appConfig.http
  private implicit val iamClientConfig: IamClientConfig = IamClientConfig(url"https://nexus.example.com/v1".value)

  private val routes =
    Routes.wrap(
      ProjectRoutes(projects)(iamClient, iamClientConfig, PaginationConfig(50, 100), global).routes
    )

  //noinspection TypeAnnotation
  trait Context {
    implicit val caller: Caller            = Caller(Identity.User("realm", "alice"), Set.empty)
    implicit val subject: Identity.Subject = caller.subject
    implicit val token: Some[AuthToken]    = Some(AuthToken("token"))

    val create = Permission.unsafe("projects/create")
    val read   = Permission.unsafe("projects/read")
    val write  = Permission.unsafe("projects/write")
    val cred   = OAuth2BearerToken("token")

    val instant = Instant.now
    val types   = Set(nxv.Project.value)
    val desc    = Some("Project description")
    val orgId   = UUID.randomUUID
    val projId  = UUID.randomUUID
    val base    = url"https://nexus.example.com/base".value
    val voc     = url"https://nexus.example.com/voc".value
    val iri     = url"http://nexus.example.com/v1/projects/org/label".value

    val payload = Json.obj(
      "description" -> Json.fromString("Project description"),
      "base"        -> Json.fromString("https://nexus.example.com/base"),
      "vocab"       -> Json.fromString("https://nexus.example.com/voc"),
      "apiMappings" -> Json.arr(
        Json.obj(
          "prefix"    -> Json.fromString("nxv"),
          "namespace" -> Json.fromString("https://bluebrain.github.io/nexus/vocabulary/")
        ),
        Json.obj(
          "prefix"    -> Json.fromString("rdf"),
          "namespace" -> Json.fromString("http://www.w3.org/1999/02/22-rdf-syntax-ns#")
        )
      )
    )
    val organization = ResourceF(
      url"http://nexus.example.com/v1/orgs/org".value,
      orgId,
      1L,
      deprecated = false,
      Set(nxv.Organization.value),
      instant,
      caller.subject,
      instant,
      caller.subject,
      Organization("org", "Org description")
    )
    val mappings = Map("nxv" -> url"https://bluebrain.github.io/nexus/vocabulary/".value,
                       "rdf" -> url"http://www.w3.org/1999/02/22-rdf-syntax-ns#".value)
    val project = ProjectDescription(desc, mappings, Some(base), Some(voc))
    val resource =
      ResourceF(iri,
                projId,
                1L,
                deprecated = false,
                types,
                instant,
                caller.subject,
                instant,
                caller.subject,
                Project("label", orgId, "org", desc, mappings, base, voc))
    val meta = resource.discard
    val replacements = Map(quote("{instant}") -> instant.toString,
                           quote("{uuid}")    -> projId.toString,
                           quote("{orgUuid}") -> orgId.toString)
  }

  "Project routes" should {

    "create a project" in new Context {
      iamClient.hasPermission(Path("/org").right.value, create)(any[Option[AuthToken]]) shouldReturn Task.pure(true)
      iamClient.identities shouldReturn Task(caller)
      projects.create("org", "label", project) shouldReturn Task(Right(meta))

      Put("/projects/org/label", payload) ~> addCredentials(cred) ~> routes ~> check {
        status shouldEqual StatusCodes.Created
        responseAs[Json].spaces2 shouldEqual jsonContentOf("/projects/meta.json", replacements).spaces2
      }
    }

    "create a project without optional fields" in new Context {
      iamClient.hasPermission(Path("/org").right.value, create)(any[Option[AuthToken]]) shouldReturn Task.pure(true)
      iamClient.identities shouldReturn Task(caller)
      projects.create("org", "label", ProjectDescription(None, Map.empty, None, None)) shouldReturn Task(Right(meta))

      Put("/projects/org/label", Json.obj()) ~> addCredentials(cred) ~> routes ~> check {
        status shouldEqual StatusCodes.Created
        responseAs[Json].spaces2 shouldEqual jsonContentOf("/projects/meta.json", replacements).spaces2
      }
    }

    "reject the creation of a project without a label" in new Context {
      iamClient.hasPermission(Path("/org").right.value, create)(any[Option[AuthToken]]) shouldReturn Task.pure(true)
      iamClient.identities shouldReturn Task(caller)

      Put("/projects/org", payload) ~> addCredentials(cred) ~> routes ~> check {
        status shouldEqual StatusCodes.MethodNotAllowed
        responseAs[Error].`@type` shouldEqual "HttpMethodNotAllowed"
      }
    }

    "reject the creation of a project which already exists" in new Context {
      iamClient.hasPermission("org" / "label", create)(any[Option[AuthToken]]) shouldReturn Task.pure(true)
      iamClient.identities shouldReturn Task(caller)
      projects.create("org", "label", project) shouldReturn Task(Left(ProjectAlreadyExists("org", "label")))

      Put("/projects/org/label", payload) ~> addCredentials(cred) ~> routes ~> check {
        status shouldEqual StatusCodes.Conflict
        responseAs[Error].`@type` shouldEqual classNameOf[ProjectAlreadyExists.type]
      }
    }

    "update a project" in new Context {
      iamClient.hasPermission("org" / "label", write)(any[Option[AuthToken]]) shouldReturn Task.pure(true)
      iamClient.identities shouldReturn Task(caller)
      projects.update("org", "label", project, 2L) shouldReturn Task(Right(meta))

      Put("/projects/org/label?rev=2", payload) ~> addCredentials(cred) ~> routes ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[Json].spaces2 shouldEqual jsonContentOf("/projects/meta.json", replacements).spaces2
      }
    }

    "reject the update of a project without name" in new Context {
      iamClient.hasPermission(Path("/org").right.value, write)(any[Option[AuthToken]]) shouldReturn Task.pure(true)
      iamClient.identities shouldReturn Task(caller)

      Put("/projects/org?rev=2", payload) ~> addCredentials(cred) ~> routes ~> check {
        status shouldEqual StatusCodes.MethodNotAllowed
        responseAs[Error].`@type` shouldEqual "HttpMethodNotAllowed"
      }
    }

    "reject the update of a non-existent project" in new Context {
      iamClient.hasPermission("org" / "label", write)(any[Option[AuthToken]]) shouldReturn Task.pure(true)
      iamClient.identities shouldReturn Task(caller)
      projects.update("org", "label", project, 2L) shouldReturn Task(Left(ProjectNotFound("org", "label")))

      Put("/projects/org/label?rev=2", payload) ~> addCredentials(cred) ~> routes ~> check {
        status shouldEqual StatusCodes.NotFound
        responseAs[Error].`@type` shouldEqual classNameOf[ProjectNotFound.type]
      }
    }

    "reject the update of a non-existent project revision" in new Context {
      iamClient.hasPermission("org" / "label", write)(any[Option[AuthToken]]) shouldReturn Task.pure(true)
      iamClient.identities shouldReturn Task(caller)
      projects.update("org", "label", project, 2L) shouldReturn Task(Left(IncorrectRev(1L, 2L)))

      Put("/projects/org/label?rev=2", payload) ~> addCredentials(cred) ~> routes ~> check {
        status shouldEqual StatusCodes.Conflict
        responseAs[Error].`@type` shouldEqual classNameOf[IncorrectRev.type]
      }
    }

    "deprecate a project" in new Context {
      iamClient.hasPermission("org" / "label", write)(any[Option[AuthToken]]) shouldReturn Task.pure(true)
      iamClient.identities shouldReturn Task(caller)
      projects.deprecate("org", "label", 2L) shouldReturn Task(Right(meta))

      Delete("/projects/org/label?rev=2") ~> addCredentials(cred) ~> routes ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[Json].spaces2 shouldEqual jsonContentOf("/projects/meta.json", replacements).spaces2
      }
    }

    "reject the deprecation of a project without rev" in new Context {
      iamClient.hasPermission("org" / "label", write)(any[Option[AuthToken]]) shouldReturn Task.pure(true)
      iamClient.identities shouldReturn Task(caller)

      Delete("/projects/org/label") ~> addCredentials(cred) ~> routes ~> check {
        status shouldEqual StatusCodes.BadRequest
        responseAs[Error].`@type` shouldEqual "MissingQueryParam"
      }
    }

    "reject the deprecation of a non-existent project" in new Context {
      iamClient.hasPermission("org" / "label", write)(any[Option[AuthToken]]) shouldReturn Task.pure(true)
      iamClient.identities shouldReturn Task(caller)
      projects.deprecate("org", "label", 2L) shouldReturn Task(Left(ProjectNotFound("org", "label")))

      Delete("/projects/org/label?rev=2") ~> addCredentials(cred) ~> routes ~> check {
        status shouldEqual StatusCodes.NotFound
        responseAs[Error].`@type` shouldEqual classNameOf[ProjectNotFound.type]
      }
    }

    "fetch a project" in new Context {
      iamClient.hasPermission("org" / "label", read)(any[Option[AuthToken]]) shouldReturn Task.pure(true)
      iamClient.identities shouldReturn Task(caller)
      projects.fetch("org", "label") shouldReturn Task(Some(resource))

      Get("/projects/org/label") ~> addCredentials(cred) ~> routes ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[Json].spaces2 shouldEqual jsonContentOf("/projects/resource.json", replacements).spaces2
      }
    }

    "return not found for a non-existent project" in new Context {
      iamClient.hasPermission("org" / "label", read)(any[Option[AuthToken]]) shouldReturn Task.pure(true)
      iamClient.identities shouldReturn Task(caller)
      projects.fetch("org", "label") shouldReturn Task(None)

      Get("/projects/org/label") ~> addCredentials(cred) ~> routes ~> check {
        status shouldEqual StatusCodes.NotFound
      }
    }

    "fetch a specific project revision" in new Context {
      iamClient.hasPermission("org" / "label", read)(any[Option[AuthToken]]) shouldReturn Task.pure(true)
      iamClient.identities shouldReturn Task(caller)
      projects.fetch("org", "label", 2L) shouldReturn Task(Right(resource))

      Get("/projects/org/label?rev=2") ~> addCredentials(cred) ~> routes ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[Json].spaces2 shouldEqual jsonContentOf("/projects/resource.json", replacements).spaces2
      }
    }

    "return not found for a non-existent project revision" in new Context {
      iamClient.hasPermission("org" / "label", read)(any[Option[AuthToken]]) shouldReturn Task.pure(true)
      iamClient.identities shouldReturn Task(caller)
      projects.fetch("org", "label", 2L) shouldReturn Task(Left(ProjectNotFound("org", "label")))

      Get("/projects/org/label?rev=2") ~> addCredentials(cred) ~> routes ~> check {
        status shouldEqual StatusCodes.NotFound
      }
    }

    "list all projects" in new Context {
      iamClient.hasPermission(Path./, read)(any[Option[AuthToken]]) shouldReturn Task.pure(true)
      iamClient.hasPermission(Path.Empty, read)(any[Option[AuthToken]]) shouldReturn Task.pure(true)
      iamClient.identities shouldReturn Task(caller)
      val projs = List(1, 2, 3).map { i =>
        val iri = Iri.Url(s"http://nexus.example.com/v1/projects/org/label$i").right.value
        UnscoredQueryResult(resource.copy(id = iri, value = resource.value.copy(label = s"label$i")))
      }
      projects.list(Pagination(0, 50)) shouldReturn Task(UnscoredQueryResults(3, projs))

      Get("/projects") ~> addCredentials(cred) ~> routes ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[Json].spaces2 shouldEqual jsonContentOf("/projects/listing.json", replacements).spaces2
      }
      Get("/projects/") ~> addCredentials(cred) ~> routes ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[Json].spaces2 shouldEqual jsonContentOf("/projects/listing.json", replacements).spaces2
      }
    }

    "list an organization projects" in new Context {
      iamClient.hasPermission(Path("/org").right.value, read)(any[Option[AuthToken]]) shouldReturn Task.pure(true)
      iamClient.hasPermission(Path("/org/").right.value, read)(any[Option[AuthToken]]) shouldReturn Task.pure(true)
      iamClient.identities shouldReturn Task(caller)
      val projs = List(1, 2, 3).map { i =>
        val iri = Iri.Url(s"http://nexus.example.com/v1/projects/org/label$i").right.value
        UnscoredQueryResult(resource.copy(id = iri, value = resource.value.copy(label = s"label$i")))
      }
      projects.list("org", Pagination(0, 50)) shouldReturn Task(UnscoredQueryResults(3, projs))

      Get("/projects/org") ~> addCredentials(cred) ~> routes ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[Json].spaces2 shouldEqual jsonContentOf("/projects/listing.json", replacements).spaces2
      }
      Get("/projects/org/") ~> addCredentials(cred) ~> routes ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[Json].spaces2 shouldEqual jsonContentOf("/projects/listing.json", replacements).spaces2
      }
    }

    "reject unauthorized requests" in new Context {
      iamClient.hasPermission("org" / "label", read)(None) shouldReturn Task.pure(false)
      iamClient.identities(None) shouldReturn Task(Caller.anonymous)

      Get("/projects/org/label") ~> routes ~> check {
        status shouldEqual StatusCodes.Forbidden
        responseAs[Error].`@type` shouldEqual "AuthorizationFailed"
      }
    }

    "reject unsupported credentials" in new Context {
      Get("/projects/org/label") ~> addCredentials(BasicHttpCredentials("something")) ~> routes ~> check {
        status shouldEqual StatusCodes.Unauthorized
        responseAs[Error].`@type` shouldEqual "AuthenticationFailed"
      }
    }

    "reject unsupported methods" in new Context {
      Options("/projects/org/label") ~> addCredentials(cred) ~> routes ~> check {
        status shouldEqual StatusCodes.MethodNotAllowed
        responseAs[Error].`@type` shouldEqual "HttpMethodNotAllowed"
      }
    }
  }
}

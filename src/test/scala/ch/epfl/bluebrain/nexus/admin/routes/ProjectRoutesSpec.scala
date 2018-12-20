package ch.epfl.bluebrain.nexus.admin.routes

import java.time.Instant
import java.util.UUID
import java.util.regex.Pattern.quote

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.{BasicHttpCredentials, OAuth2BearerToken}
import akka.http.scaladsl.testkit.ScalatestRouteTest
import ch.epfl.bluebrain.nexus.admin.CommonRejection.IllegalParameter
import ch.epfl.bluebrain.nexus.admin.Error
import ch.epfl.bluebrain.nexus.admin.Error._
import ch.epfl.bluebrain.nexus.admin.config.AppConfig.PaginationConfig
import ch.epfl.bluebrain.nexus.admin.config.Vocabulary.nxv
import ch.epfl.bluebrain.nexus.admin.marshallers.instances._
import ch.epfl.bluebrain.nexus.admin.organizations.Organization
import ch.epfl.bluebrain.nexus.admin.projects.ProjectRejection._
import ch.epfl.bluebrain.nexus.admin.projects.{Project, Projects}
import ch.epfl.bluebrain.nexus.admin.types.ResourceF
import ch.epfl.bluebrain.nexus.commons.test.Resources
import ch.epfl.bluebrain.nexus.commons.types.HttpRejection.{MethodNotSupported, MissingParameters, UnauthorizedAccess}
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

  private implicit val iamClientConfig: IamClientConfig = IamClientConfig("v1", url"https://nexus.example.com".value)

  private val routes = ProjectRoutes(projects)(iamClient, iamClientConfig, PaginationConfig(0, 50, 100), global).routes

  //noinspection TypeAnnotation
  trait Context {
    implicit val caller: Caller            = Caller(Identity.User("realm", "alice"), Set.empty)
    implicit val subject: Identity.Subject = caller.subject
    implicit val token: Some[AuthToken]    = Some(AuthToken("token"))

    val read  = Permission.unsafe("projects/read")
    val write = Permission.unsafe("projects/write")
    val cred  = OAuth2BearerToken("token")

    val instant = Instant.now
    val types   = Set(nxv.Project.value)
    val desc    = Some("Project description")
    val orgId   = UUID.randomUUID
    val projId  = UUID.randomUUID
    val base    = url"https://nexus.example.com/base".value
    val iri     = url"http://nexus.example.com/v1/projects/org/label".value

    val payload = Json.obj(
      "description" -> Json.fromString("Project description"),
      "base"        -> Json.fromString("https://nexus.example.com/base"),
      "apiMappings" -> Json.arr(
        Json.obj(
          "prefix"    -> Json.fromString("nxv"),
          "namespace" -> Json.fromString("https://bluebrain.github.io/nexus/vocabulary/")
        ),
        Json.obj(
          "prefix"    -> Json.fromString("rdf"),
          "namespace" -> Json.fromString("http://www.w3.org/1999/02/22-rdf-syntax-ns#type")
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
                       "rdf" -> url"http://www.w3.org/1999/02/22-rdf-syntax-ns#type".value)
    val project = Project("label", "org", desc, mappings, base)
    val resource =
      ResourceF(iri, projId, 1L, deprecated = false, types, instant, caller.subject, instant, caller.subject, project)
    val meta         = resource.discard
    val replacements = Map(quote("{instant}") -> instant.toString, quote("{uuid}") -> projId.toString)
  }

  "Project routes" should {

    "create a project" in new Context {
      iamClient.authorizeOn("org" / "label", write) shouldReturn Task.unit
      iamClient.identities shouldReturn Task(caller)
      projects.create(project) shouldReturn Task(Right(meta))

      Put("/projects/org/label", payload) ~> addCredentials(cred) ~> routes ~> check {
        status shouldEqual StatusCodes.Created
        responseAs[Json] shouldEqual jsonContentOf("/projects/meta.json", replacements)
      }
    }

    "create a project without a description" in new Context {
      iamClient.authorizeOn("org" / "label", write) shouldReturn Task.unit
      iamClient.identities shouldReturn Task(caller)
      projects.create(Project("label", "org", None, Map.empty, base)) shouldReturn Task(Right(meta))

      Put("/projects/org/label", Json.obj("base" -> Json.fromString(base.asString))) ~> addCredentials(cred) ~> routes ~> check {
        status shouldEqual StatusCodes.Created
        responseAs[Json] shouldEqual jsonContentOf("/projects/meta.json", replacements)
      }
    }

    "reject the creation of a project without a label" in new Context {
      iamClient.authorizeOn(Path("/org").right.value, write) shouldReturn Task.unit
      iamClient.identities shouldReturn Task(caller)

      Put("/projects/org", payload) ~> addCredentials(cred) ~> routes ~> check {
        status shouldEqual StatusCodes.BadRequest
        responseAs[Error].code shouldEqual classNameOf[IllegalParameter.type]
      }
    }

    "reject the creation of a project which already exists" in new Context {
      iamClient.authorizeOn("org" / "label", write) shouldReturn Task.unit
      iamClient.identities shouldReturn Task(caller)
      projects.create(project) shouldReturn Task(Left(ProjectExists))

      Put("/projects/org/label", payload) ~> addCredentials(cred) ~> routes ~> check {
        status shouldEqual StatusCodes.Conflict
        responseAs[Error].code shouldEqual classNameOf[ProjectExists.type]
      }
    }

    "update a project" in new Context {
      iamClient.authorizeOn("org" / "label", write) shouldReturn Task.unit
      iamClient.identities shouldReturn Task(caller)
      projects.update(project, 2L) shouldReturn Task(Right(meta))

      Put("/projects/org/label?rev=2", payload) ~> addCredentials(cred) ~> routes ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[Json] shouldEqual jsonContentOf("/projects/meta.json", replacements)
      }
    }

    "reject the update of a project without name" in new Context {
      iamClient.authorizeOn(Path("/org").right.value, write) shouldReturn Task.unit
      iamClient.identities shouldReturn Task(caller)

      Put("/projects/org?rev=2", payload) ~> addCredentials(cred) ~> routes ~> check {
        status shouldEqual StatusCodes.BadRequest
        responseAs[Error].code shouldEqual classNameOf[IllegalParameter.type]
      }
    }

    "reject the update of a non-existent project" in new Context {
      iamClient.authorizeOn("org" / "label", write) shouldReturn Task.unit
      iamClient.identities shouldReturn Task(caller)
      projects.update(project, 2L) shouldReturn Task(Left(ProjectNotFound))

      Put("/projects/org/label?rev=2", payload) ~> addCredentials(cred) ~> routes ~> check {
        status shouldEqual StatusCodes.NotFound
        responseAs[Error].code shouldEqual classNameOf[ProjectNotFound.type]
      }
    }

    "reject the update of a non-existent project revision" in new Context {
      iamClient.authorizeOn("org" / "label", write) shouldReturn Task.unit
      iamClient.identities shouldReturn Task(caller)
      projects.update(project, 2L) shouldReturn Task(Left(IncorrectRev(1L, 2L)))

      Put("/projects/org/label?rev=2", payload) ~> addCredentials(cred) ~> routes ~> check {
        status shouldEqual StatusCodes.Conflict
        responseAs[Error].code shouldEqual classNameOf[IncorrectRev.type]
      }
    }

    "deprecate a project" in new Context {
      iamClient.authorizeOn("org" / "label", write) shouldReturn Task.unit
      iamClient.identities shouldReturn Task(caller)
      projects.deprecate("org", "label", 2L) shouldReturn Task(Right(meta))

      Delete("/projects/org/label?rev=2") ~> addCredentials(cred) ~> routes ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[Json] shouldEqual jsonContentOf("/projects/meta.json", replacements)
      }
    }

    "reject the deprecation of a project without rev" in new Context {
      iamClient.authorizeOn("org" / "label", write) shouldReturn Task.unit
      iamClient.identities shouldReturn Task(caller)

      Delete("/projects/org/label") ~> addCredentials(cred) ~> routes ~> check {
        status shouldEqual StatusCodes.BadRequest
        responseAs[Error].code shouldEqual classNameOf[MissingParameters.type]
      }
    }

    "reject the deprecation of a non-existent project" in new Context {
      iamClient.authorizeOn("org" / "label", write) shouldReturn Task.unit
      iamClient.identities shouldReturn Task(caller)
      projects.deprecate("org", "label", 2L) shouldReturn Task(Left(ProjectNotFound))

      Delete("/projects/org/label?rev=2") ~> addCredentials(cred) ~> routes ~> check {
        status shouldEqual StatusCodes.NotFound
        responseAs[Error].code shouldEqual classNameOf[ProjectNotFound.type]
      }
    }

    "fetch a project" in new Context {
      iamClient.authorizeOn("org" / "label", read) shouldReturn Task.unit
      iamClient.identities shouldReturn Task(caller)
      projects.fetch("org", "label") shouldReturn Task(Some(resource))

      Get("/projects/org/label") ~> addCredentials(cred) ~> routes ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[Json] shouldEqual jsonContentOf("/projects/resource.json", replacements)
      }
    }

    "return not found for a non-existent project" in new Context {
      iamClient.authorizeOn("org" / "label", read) shouldReturn Task.unit
      iamClient.identities shouldReturn Task(caller)
      projects.fetch("org", "label") shouldReturn Task(None)

      Get("/projects/org/label") ~> addCredentials(cred) ~> routes ~> check {
        status shouldEqual StatusCodes.NotFound
      }
    }

    "fetch a specific project revision" in new Context {
      iamClient.authorizeOn("org" / "label", read) shouldReturn Task.unit
      iamClient.identities shouldReturn Task(caller)
      projects.fetch("org", "label", 2L) shouldReturn Task(Right(resource))

      Get("/projects/org/label?rev=2") ~> addCredentials(cred) ~> routes ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[Json] shouldEqual jsonContentOf("/projects/resource.json", replacements)
      }
    }

    "return not found for a non-existent project revision" in new Context {
      iamClient.authorizeOn("org" / "label", read) shouldReturn Task.unit
      iamClient.identities shouldReturn Task(caller)
      projects.fetch("org", "label", 2L) shouldReturn Task(Left(ProjectNotFound))

      Get("/projects/org/label?rev=2") ~> addCredentials(cred) ~> routes ~> check {
        status shouldEqual StatusCodes.NotFound
      }
    }

    "list all projects" in new Context {
      iamClient.authorizeOn(Path./, read) shouldReturn Task.unit
      iamClient.authorizeOn(Path.Empty, read) shouldReturn Task.unit
      iamClient.identities shouldReturn Task(caller)
      val projs = List(1, 2, 3).map { i =>
        val iri = Iri.Url(s"http://nexus.example.com/v1/projects/org/label$i").right.value
        UnscoredQueryResult(resource.copy(id = iri, value = resource.value.copy(label = s"label$i")))
      }
      projects.list(Pagination(0, 50)) shouldReturn Task(UnscoredQueryResults(3, projs))

      Get("/projects") ~> addCredentials(cred) ~> routes ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[Json] shouldEqual jsonContentOf("/projects/listing.json", replacements)
      }
      Get("/projects/") ~> addCredentials(cred) ~> routes ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[Json] shouldEqual jsonContentOf("/projects/listing.json", replacements)
      }
    }

    "list an organization projects" in new Context {
      iamClient.authorizeOn(Path("/org").right.value, read) shouldReturn Task.unit
      iamClient.authorizeOn(Path("/org/").right.value, read) shouldReturn Task.unit
      iamClient.identities shouldReturn Task(caller)
      val projs = List(1, 2, 3).map { i =>
        val iri = Iri.Url(s"http://nexus.example.com/v1/projects/org/label$i").right.value
        UnscoredQueryResult(resource.copy(id = iri, value = resource.value.copy(label = s"label$i")))
      }
      projects.list("org", Pagination(0, 50)) shouldReturn Task(UnscoredQueryResults(3, projs))

      Get("/projects/org") ~> addCredentials(cred) ~> routes ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[Json] shouldEqual jsonContentOf("/projects/listing.json", replacements)
      }
      Get("/projects/org/") ~> addCredentials(cred) ~> routes ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[Json] shouldEqual jsonContentOf("/projects/listing.json", replacements)
      }
    }

    "reject unauthorized requests" in new Context {
      iamClient.authorizeOn("org" / "label", read)(None) shouldReturn Task.raiseError(UnauthorizedAccess)
      iamClient.identities(None) shouldReturn Task(Caller.anonymous)

      Get("/projects/org/label") ~> routes ~> check {
        status shouldEqual StatusCodes.Unauthorized
        responseAs[Error].code shouldEqual classNameOf[UnauthorizedAccess.type]
      }
    }

    "reject unsupported credentials" in new Context {
      Get("/projects/org/label") ~> addCredentials(BasicHttpCredentials("something")) ~> routes ~> check {
        status shouldEqual StatusCodes.Unauthorized
        responseAs[Error].code shouldEqual classNameOf[UnauthorizedAccess.type]
      }
    }

    "reject unsupported methods" in new Context {
      Options("/projects/org/label") ~> addCredentials(cred) ~> routes ~> check {
        status shouldEqual StatusCodes.MethodNotAllowed
        responseAs[Error].code shouldEqual classNameOf[MethodNotSupported.type]
      }
    }
  }
}
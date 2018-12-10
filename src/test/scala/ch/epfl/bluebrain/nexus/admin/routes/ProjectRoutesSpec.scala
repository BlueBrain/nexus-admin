package ch.epfl.bluebrain.nexus.admin.routes

import java.time.Instant
import java.util.UUID

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.OAuth2BearerToken
import akka.http.scaladsl.testkit.ScalatestRouteTest
import ch.epfl.bluebrain.nexus.admin.config.Vocabulary.nxv
import ch.epfl.bluebrain.nexus.admin.marshallers.instances._
import ch.epfl.bluebrain.nexus.admin.organizations.Organization
import ch.epfl.bluebrain.nexus.admin.projects.{Project, Projects}
import ch.epfl.bluebrain.nexus.admin.types.ResourceF
import ch.epfl.bluebrain.nexus.commons.test.Randomness
import ch.epfl.bluebrain.nexus.iam.client.IamClient
import ch.epfl.bluebrain.nexus.iam.client.config.IamClientConfig
import ch.epfl.bluebrain.nexus.iam.client.types.{AuthToken, Caller, Identity, Permission}
import ch.epfl.bluebrain.nexus.rdf.Iri.Path._
import ch.epfl.bluebrain.nexus.rdf.syntax.node.unsafe._
import io.circe.syntax._
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
    with Randomness
    with Matchers {

  private val iamClient = mock[IamClient[Task]]
  private val projects  = mock[Projects[Task]]

  private val routes =
    ProjectRoutes(projects)(iamClient, IamClientConfig("v1", url"https://nexus.example.com".value), global).routes

  trait Context {
    implicit val caller: Caller = Caller(Identity.User("realm", "alice"), Set.empty)
    implicit val subject: Identity.Subject = caller.subject
    implicit val token: Some[AuthToken] = Some(AuthToken("token"))

    val read  = Permission.unsafe("projects/read")
    val write = Permission.unsafe("projects/write")
    val cred = OAuth2BearerToken("token")

    val instant = Instant.now
    val types  = Set(nxv.Project.value)
    val desc   = Some("Project description")
    val orgId  = UUID.randomUUID
    val projId = UUID.randomUUID
    val iri    = url"http://nexus.example.com/v1/projects/org/proj".value
    val organization = ResourceF(
      url"http://nexus.example.com/v1/orgs/org".value,
      orgId,
      1L,
      false,
      Set(nxv.Organization.value),
      instant,
      caller.subject,
      instant,
      caller.subject,
      Organization("org", "Org description")
    )
    val project    = Project("proj", "org", desc)
    val resource = ResourceF(iri, projId, 1L, false, types, instant, caller.subject, instant, caller.subject, project)
    val meta = resource.discard
  }

  "Project routes" should {

    "create a project" in new Context {
      iamClient.authorizeOn("org" / "label", write) shouldReturn Task.unit
      iamClient.getCaller(filterGroups = true) shouldReturn Task(caller)
      projects.create(project) shouldReturn Task(Right(meta))

      Put("/projects/org/label", project.asJson) ~> addCredentials(cred) ~> routes ~> check {
        status shouldEqual StatusCodes.Created
      }
    }

    "fetch a project" in new Context {
      iamClient.authorizeOn("org" / "label", read) shouldReturn Task.unit
      iamClient.getCaller(filterGroups = true) shouldReturn Task(caller)
      projects.fetch("org", "label") shouldReturn Task(Some(resource))

      Get("/projects/org/label") ~> addCredentials(cred) ~> routes ~> check {
        status shouldEqual StatusCodes.OK
      }
    }
  }
}

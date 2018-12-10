package ch.epfl.bluebrain.nexus.admin.routes
import java.time.Instant
import java.util.UUID

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.OAuth2BearerToken
import akka.http.scaladsl.testkit.ScalatestRouteTest
import ch.epfl.bluebrain.nexus.admin.config.Vocabulary.nxv
import ch.epfl.bluebrain.nexus.admin.marshallers.instances._
import ch.epfl.bluebrain.nexus.admin.organizations.{Organization, Organizations}
import ch.epfl.bluebrain.nexus.admin.types.ResourceF
import ch.epfl.bluebrain.nexus.commons.test.Randomness
import ch.epfl.bluebrain.nexus.iam.client.IamClient
import ch.epfl.bluebrain.nexus.iam.client.config.IamClientConfig
import ch.epfl.bluebrain.nexus.iam.client.types.{AuthToken, Caller, Identity, Permission}
import ch.epfl.bluebrain.nexus.rdf.Iri.Path
import ch.epfl.bluebrain.nexus.rdf.syntax.node.unsafe._
import io.circe.syntax._
import monix.eval.Task
import monix.execution.Scheduler.global
import org.mockito.integrations.scalatest.IdiomaticMockitoFixture
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{EitherValues, Matchers, WordSpecLike}

class OrganizationRoutesSpec extends WordSpecLike
  with IdiomaticMockitoFixture
  with ScalatestRouteTest
  with ScalaFutures
  with EitherValues
  with Randomness
  with Matchers {

  private val iamClient = mock[IamClient[Task]]
  private val organizations = mock[Organizations[Task]]

  private val routes = OrganizationRoutes(organizations)(iamClient, IamClientConfig("v1", url"https://nexus.example.com".value), global).routes

  trait Context {
    implicit val caller: Caller = Caller(Identity.User("realm", "alice"), Set.empty)
    implicit val subject: Identity.Subject = caller.subject
    implicit val token: Some[AuthToken] = Some(AuthToken("token"))

    val read  = Permission.unsafe("projects/read")
    val write = Permission.unsafe("projects/write")
    val cred = OAuth2BearerToken("token")

    val instant = Instant.now
    val types  = Set(nxv.Organization.value)
    val orgId  = UUID.randomUUID
    val iri    = url"http://nexus.example.com/v1/orgs/org".value

    val organization = Organization("org", "Org description")
    val resource = ResourceF(iri, orgId, 1L, false, types, instant, caller.subject, instant, caller.subject, organization)
    val meta = resource.discard
  }

  "Organizations routes" should {

    "create an organization" in new Context {
      iamClient.authorizeOn(Path("/org").right.value, write) shouldReturn Task.unit
      iamClient.getCaller(filterGroups = true) shouldReturn Task(caller)
      organizations.create(organization) shouldReturn Task(Right(meta))

      Put("/orgs/org", organization.asJson) ~> addCredentials(cred) ~> routes ~> check {
        status shouldEqual StatusCodes.Created
      }
    }

    "fetch an organization" in new Context {
      iamClient.authorizeOn(Path("/org").right.value, read) shouldReturn Task.unit
      iamClient.getCaller(filterGroups = true) shouldReturn Task(caller)
      organizations.fetch("org") shouldReturn Task(Some(resource))

      Get("/orgs/org") ~> addCredentials(cred) ~> routes ~> check {
        status shouldEqual StatusCodes.OK
      }
    }
  }
}

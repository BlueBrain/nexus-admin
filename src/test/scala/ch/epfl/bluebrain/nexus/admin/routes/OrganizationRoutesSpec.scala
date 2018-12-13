package ch.epfl.bluebrain.nexus.admin.routes
import java.time.Instant
import java.util.UUID
import java.util.regex.Pattern.quote

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.{BasicHttpCredentials, OAuth2BearerToken}
import akka.http.scaladsl.testkit.ScalatestRouteTest
import ch.epfl.bluebrain.nexus.admin.CommonRejection._
import ch.epfl.bluebrain.nexus.admin.Error
import ch.epfl.bluebrain.nexus.admin.Error.classNameOf
import ch.epfl.bluebrain.nexus.admin.config.AppConfig.PaginationConfig
import ch.epfl.bluebrain.nexus.admin.config.Vocabulary.nxv
import ch.epfl.bluebrain.nexus.admin.marshallers.instances._
import ch.epfl.bluebrain.nexus.admin.organizations.OrganizationRejection._
import ch.epfl.bluebrain.nexus.admin.organizations.{Organization, Organizations}
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
import ch.epfl.bluebrain.nexus.rdf.syntax.node.unsafe._
import io.circe.Json
import io.circe.syntax._
import monix.eval.Task
import monix.execution.Scheduler.global
import org.mockito.integrations.scalatest.IdiomaticMockitoFixture
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{EitherValues, Matchers, WordSpecLike}

class OrganizationRoutesSpec
    extends WordSpecLike
    with IdiomaticMockitoFixture
    with ScalatestRouteTest
    with ScalaFutures
    with EitherValues
    with Resources
    with Matchers {

  private val iamClient     = mock[IamClient[Task]]
  private val organizations = mock[Organizations[Task]]

  private implicit val iamClientConfig: IamClientConfig = IamClientConfig("v1", url"https://nexus.example.com".value)

  private val routes =
    OrganizationRoutes(organizations)(iamClient, iamClientConfig, PaginationConfig(0, 50, 100), global).routes

  //noinspection TypeAnnotation
  trait Context {
    implicit val caller: Caller            = Caller(Identity.User("realm", "alice"), Set.empty)
    implicit val subject: Identity.Subject = caller.subject
    implicit val token: Some[AuthToken]    = Some(AuthToken("token"))

    val read  = Permission.unsafe("organizations/read")
    val write = Permission.unsafe("organizations/write")
    val cred  = OAuth2BearerToken("token")

    val instant = Instant.now
    val types   = Set(nxv.Organization.value)
    val orgId   = UUID.randomUUID
    val iri     = url"http://nexus.example.com/v1/orgs/org".value
    val path    = Path("/org").right.value

    val organization = Organization("org", "Org description")
    val resource = ResourceF(iri,
                             orgId,
                             1L,
                             deprecated = false,
                             types,
                             instant,
                             caller.subject,
                             instant,
                             caller.subject,
                             organization)
    val meta         = resource.discard
    val replacements = Map(quote("{instant}") -> instant.toString, quote("{uuid}") -> orgId.toString)
  }

  "Organizations routes" should {

    "create an organization" in new Context {
      iamClient.authorizeOn(path, write) shouldReturn Task.unit
      iamClient.getCaller shouldReturn Task(caller)
      organizations.create(organization) shouldReturn Task(Right(meta))

      Put("/orgs/org", organization.asJson) ~> addCredentials(cred) ~> routes ~> check {
        status shouldEqual StatusCodes.Created
        responseAs[Json] shouldEqual jsonContentOf("/orgs/meta.json", replacements)
      }
    }

    "reject the creation of an organization which already exists" in new Context {
      iamClient.authorizeOn(path, write) shouldReturn Task.unit
      iamClient.getCaller shouldReturn Task(caller)
      organizations.create(organization) shouldReturn Task(Left(OrganizationExists))

      Put("/orgs/org", organization.asJson) ~> addCredentials(cred) ~> routes ~> check {
        status shouldEqual StatusCodes.Conflict
        responseAs[Error].code shouldEqual classNameOf[OrganizationExists.type]
      }
    }

    "reject the creation of an organization without a label" in new Context {
      iamClient.authorizeOn(Path./, write) shouldReturn Task.unit
      iamClient.getCaller shouldReturn Task(caller)

      Put("/orgs/", organization.asJson) ~> addCredentials(cred) ~> routes ~> check {
        status shouldEqual StatusCodes.BadRequest
        responseAs[Error].code shouldEqual classNameOf[IllegalParameter.type]
      }
    }

    "reject the creation of an organization with a wrong name" in new Context {
      iamClient.authorizeOn(Path("/foo").right.value, write) shouldReturn Task.unit
      iamClient.getCaller shouldReturn Task(caller)

      Put("/orgs/foo", organization.asJson) ~> addCredentials(cred) ~> routes ~> check {
        status shouldEqual StatusCodes.BadRequest
        responseAs[Error].code shouldEqual classNameOf[IllegalParameter.type]
      }
    }

    "fetch an organization" in new Context {
      iamClient.authorizeOn(path, read) shouldReturn Task.unit
      iamClient.getCaller shouldReturn Task(caller)
      organizations.fetch("org") shouldReturn Task(Some(resource))

      Get("/orgs/org") ~> addCredentials(cred) ~> routes ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[Json] shouldEqual jsonContentOf("/orgs/resource.json", replacements)
      }
    }

    "return not found for a non-existent organization" in new Context {
      iamClient.authorizeOn(path, read) shouldReturn Task.unit
      iamClient.getCaller shouldReturn Task(caller)
      organizations.fetch("org") shouldReturn Task(None)

      Get("/orgs/org") ~> addCredentials(cred) ~> routes ~> check {
        status shouldEqual StatusCodes.NotFound
      }
    }

    "fetch a specific organization revision" in new Context {
      iamClient.authorizeOn(path, read) shouldReturn Task.unit
      iamClient.getCaller shouldReturn Task(caller)
      organizations.fetch("org", 2L) shouldReturn Task(Some(resource))

      Get("/orgs/org?rev=2") ~> addCredentials(cred) ~> routes ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[Json] shouldEqual jsonContentOf("/orgs/resource.json", replacements)
      }
    }

    "deprecate an organization" in new Context {
      iamClient.authorizeOn(path, read) shouldReturn Task.unit
      iamClient.getCaller shouldReturn Task(caller)
      organizations.deprecate("org", 2L) shouldReturn Task(Right(meta))

      Delete("/orgs/org?rev=2") ~> addCredentials(cred) ~> routes ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[Json] shouldEqual jsonContentOf("/orgs/meta.json", replacements)
      }
    }

    "reject the deprecation of an organization without rev" in new Context {
      iamClient.authorizeOn(path, read) shouldReturn Task.unit
      iamClient.getCaller shouldReturn Task(caller)

      Delete("/orgs/org") ~> addCredentials(cred) ~> routes ~> check {
        status shouldEqual StatusCodes.BadRequest
        responseAs[Error].code shouldEqual classNameOf[MissingParameters.type]
      }
    }

    "reject the deprecation of an organization with incorrect rev" in new Context {
      iamClient.authorizeOn(path, read) shouldReturn Task.unit
      iamClient.getCaller shouldReturn Task(caller)
      organizations.deprecate("org", 2L) shouldReturn Task(Left(IncorrectRev(3L, 2L)))

      Delete("/orgs/org?rev=2") ~> addCredentials(cred) ~> routes ~> check {
        status shouldEqual StatusCodes.Conflict
        responseAs[Error].code shouldEqual classNameOf[IncorrectRev.type]
      }
    }

    "reject unauthorized requests" in new Context {
      iamClient.authorizeOn(path, read)(None) shouldReturn Task.raiseError(UnauthorizedAccess)
      iamClient.getCaller(None) shouldReturn Task(Caller.anonymous)

      Get("/orgs/org") ~> routes ~> check {
        status shouldEqual StatusCodes.Unauthorized
        responseAs[Error].code shouldEqual classNameOf[UnauthorizedAccess.type]
      }
    }

    "list organizations" in new Context {
      iamClient.authorizeOn(Path./, read) shouldReturn Task.unit
      iamClient.authorizeOn(Path.Empty, read) shouldReturn Task.unit
      iamClient.getCaller shouldReturn Task(caller)

      val orgs = List(1, 2, 3).map { i =>
        val iri = Iri.Url(s"http://nexus.example.com/v1/orgs/org$i").right.value
        UnscoredQueryResult(resource.copy(id = iri, value = resource.value.copy(label = s"org$i")))
      }
      organizations.list(Pagination(0, 50)) shouldReturn Task(UnscoredQueryResults(3, orgs))

      Get("/orgs") ~> addCredentials(cred) ~> routes ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[Json] shouldEqual jsonContentOf("/orgs/listing.json", replacements)
      }
      Get("/orgs/") ~> addCredentials(cred) ~> routes ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[Json] shouldEqual jsonContentOf("/orgs/listing.json", replacements)
      }
    }

    "reject unsupported credentials" in new Context {
      Get("/orgs/org") ~> addCredentials(BasicHttpCredentials("something")) ~> routes ~> check {
        status shouldEqual StatusCodes.Unauthorized
        responseAs[Error].code shouldEqual classNameOf[UnauthorizedAccess.type]
      }
    }

    "reject unsupported methods" in new Context {
      Options("/orgs/org") ~> addCredentials(cred) ~> routes ~> check {
        status shouldEqual StatusCodes.MethodNotAllowed
        responseAs[Error].code shouldEqual classNameOf[MethodNotSupported.type]
      }
    }
  }
}

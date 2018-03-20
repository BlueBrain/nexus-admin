package ch.epfl.bluebrain.nexus.admin.service.directives

import akka.http.scaladsl.model.headers.OAuth2BearerToken
import akka.http.scaladsl.model.{StatusCodes, Uri}
import akka.http.scaladsl.server
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.testkit.ScalatestRouteTest
import ch.epfl.bluebrain.nexus.admin.core.Error
import ch.epfl.bluebrain.nexus.admin.core.Error._
import ch.epfl.bluebrain.nexus.admin.service.rejections.CommonRejections.DownstreamServiceError
import ch.epfl.bluebrain.nexus.admin.refined.permissions._
import ch.epfl.bluebrain.nexus.admin.service.directives.AuthDirectives._
import ch.epfl.bluebrain.nexus.admin.service.handlers.{ExceptionHandling, RejectionHandling}
import ch.epfl.bluebrain.nexus.commons.http.ContextUri
import ch.epfl.bluebrain.nexus.commons.http.JsonLdCirceSupport._
import ch.epfl.bluebrain.nexus.commons.iam.IamClient
import ch.epfl.bluebrain.nexus.commons.iam.acls.{FullAccessControlList, Path, Permission, Permissions}
import ch.epfl.bluebrain.nexus.commons.iam.auth.{AuthenticatedUser, User}
import ch.epfl.bluebrain.nexus.commons.iam.identity.Caller.{AnonymousCaller, AuthenticatedCaller}
import ch.epfl.bluebrain.nexus.commons.iam.identity.Identity.{Anonymous, GroupRef}
import ch.epfl.bluebrain.nexus.commons.iam.identity.{Caller, IdentityId}
import ch.epfl.bluebrain.nexus.commons.test.Randomness
import ch.epfl.bluebrain.nexus.commons.types.HttpRejection.UnauthorizedAccess
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.auto._
import io.circe.syntax._
import io.circe.{Encoder, Json}
import org.mockito.Mockito
import org.mockito.Mockito._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{BeforeAndAfter, Inspectors, Matchers, WordSpecLike}

import scala.concurrent.Future
import scala.concurrent.duration._

class AuthDirectivesSpec
    extends WordSpecLike
    with ScalatestRouteTest
    with Matchers
    with ScalaFutures
    with Randomness
    with MockitoSugar
    with BeforeAndAfter
    with Inspectors {

  private implicit val credOptionEncoder: Encoder[OAuth2BearerToken] =
    Encoder.encodeString.contramap {
      case OAuth2BearerToken(token) => s"$token"
    }

  private implicit val config: Configuration = Configuration.default.withDiscriminator("type")
  private val ErrorContext                   = ContextUri(Uri("http://nexus.example.com/contexts/nexus/core/error/v0.1.0"))

  private def route()(implicit cred: Option[OAuth2BearerToken]) = {
    handleRejections(RejectionHandling.rejectionHandler(ErrorContext)) {
      (get & authCaller) { caller =>
        complete(caller.asJson)
      }
    }
  }

  private implicit val cl: IamClient[Future] = mock[IamClient[Future]]

  private def handler(route: => server.Route) =
    (handleExceptions(ExceptionHandling.exceptionHandler(ErrorContext)) & handleRejections(
      RejectionHandling.rejectionHandler(ErrorContext)))(route)

  before {
    Mockito.reset(cl)
  }

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(3 seconds, 100 milliseconds)

  "An AuthorizationDirectives" should {
    val ValidCredentials = OAuth2BearerToken("validToken")

    val group1     = GroupRef(IdentityId("localhost:8080/v0/realms/BBP/groups/group1"))
    val group2     = GroupRef(IdentityId("localhost:8080/v0/realms/BBP/groups/group2"))
    val user: User = AuthenticatedUser(Set(group1, group2))

    val acl = FullAccessControlList((group1, Path("projects/proj"), Permissions(Permission("projects/read"))))

    "return unauthorized when the request contains an invalid token" in {
      implicit val cred: Option[OAuth2BearerToken] = Some(OAuth2BearerToken("invalid"))
      when(cl.getCaller(cred, filterGroups = true)).thenReturn(Future.failed(UnauthorizedAccess))

      Get("projects/proj") ~> route() ~> check {
        status shouldEqual StatusCodes.Unauthorized
        responseAs[Error].code shouldEqual classNameOf[UnauthorizedAccess.type]
      }
    }

    "return internal server error when the downstream service is down" in {
      implicit val cred: Option[OAuth2BearerToken] = Some(OAuth2BearerToken("invalid"))
      when(cl.getCaller(cred, filterGroups = true)).thenReturn(Future.failed(new RuntimeException("downstream error")))

      Get("projects/proj") ~> route() ~> check {
        status shouldEqual StatusCodes.InternalServerError
        responseAs[Error].code shouldEqual classNameOf[DownstreamServiceError.type]
      }
    }

    "return an authenticated caller when the request contains a valid token" in {
      implicit val cred: Option[OAuth2BearerToken] = Some(ValidCredentials)
      val expectedCaller                           = AuthenticatedCaller(ValidCredentials, user)
      when(cl.getCaller(cred, filterGroups = true)).thenReturn(Future.successful(expectedCaller))
      Get("projects/proj") ~> route() ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[Json] shouldEqual (AuthenticatedCaller(ValidCredentials, user): Caller).asJson
      }
    }

    "return an anonymous caller when the request contains a valid token" in {
      implicit val cred: Option[OAuth2BearerToken] = None
      val expectedCaller: Caller                   = AnonymousCaller(Uri("http://example.nexus.com/iam"))
      when(cl.getCaller(cred, filterGroups = true)).thenReturn(Future.successful(expectedCaller))

      Get("projects/proj") ~> route() ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[Json] shouldEqual expectedCaller.asJson
      }
    }

    "return unauthorized when the requested path does not contains read permission" in {
      implicit val cred: Option[OAuth2BearerToken] = Some(ValidCredentials)
      val path                                     = "projects/proj/config"
      when(cl.getAcls(Path(path), parents = true, self = true)).thenReturn(Future.successful(FullAccessControlList()))

      Get(path) ~> handler((get & authorizeOn[HasReadProjects](Path(path)))(_ => complete("Success"))) ~> check {
        status shouldEqual StatusCodes.Unauthorized
        responseAs[Error].code shouldEqual classNameOf[UnauthorizedAccess.type]
      }
    }

    "return unauthorized when the token on the cred is wrong" in {
      implicit val cred: Option[OAuth2BearerToken] = Some(OAuth2BearerToken("Invalid"))
      val path                                     = "projects/proj"
      when(cl.getAcls(Path(path), parents = true, self = true)).thenReturn(Future.failed(UnauthorizedAccess))

      Get(path) ~> handler((get & authorizeOn[HasReadProjects](Path(path)))(_ => complete("Success"))) ~> check {
        status shouldEqual StatusCodes.Unauthorized
        responseAs[Error].code shouldEqual classNameOf[UnauthorizedAccess.type]
      }
    }

    "return authorized when the requested path for the authenticated cred contains read permission" in {
      implicit val cred: Option[OAuth2BearerToken] = Some(ValidCredentials)
      val path                                     = "projects/proj"
      when(cl.getAcls(Path(path), parents = true, self = true)).thenReturn(Future.successful(acl))

      Get(path) ~> handler((get & authorizeOn[HasReadProjects](Path(path)))(_ => complete("Success"))) ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[String] shouldEqual "Success"
      }
    }

    "return authorized when the requested path for the anon cred contains write permission" in {
      implicit val cred: Option[OAuth2BearerToken] = None
      val path                                     = "/projects/test"
      val acl = FullAccessControlList(
        (Anonymous(), Path("projects/test"), Permissions(Permission("projects/manage"), Permission("projects/other"))))
      when(cl.getAcls(Path(path), parents = true, self = true)).thenReturn(Future.successful(acl))
      forAll(
        List(
          handler((get & authorizeOn[HasReadProjects](Path(path)))(_ => complete("Success"))),
          handler((get & authorizeOn[HasWriteProjects](Path(path)))(_ => complete("Success"))),
          handler((get & authorizeOn[HasCreateProjects](Path(path)))(_ => complete("Success"))),
          handler((get & authorizeOn[HasManageProjects](Path(path)))(_ => complete("Success")))
        )) { r =>
        Get(path) ~> r ~> check {
          status shouldEqual StatusCodes.OK
          responseAs[String] shouldEqual "Success"
        }
      }
    }

    "return internal server error when authorizing and the downstream service is down" in {
      implicit val cred: Option[OAuth2BearerToken] = Some(ValidCredentials)
      val path                                     = "projects/proj"
      when(cl.getAcls(Path(path), parents = true, self = true))
        .thenReturn(Future.failed(new RuntimeException("downstream error")))

      Get(path) ~> handler((get & authorizeOn[HasReadProjects](Path(path)))(_ => complete("Success"))) ~> check {
        status shouldEqual StatusCodes.InternalServerError
        responseAs[Error].code shouldEqual classNameOf[DownstreamServiceError.type]
      }
    }
  }
}

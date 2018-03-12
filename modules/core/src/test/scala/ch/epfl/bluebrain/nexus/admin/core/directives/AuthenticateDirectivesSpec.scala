package ch.epfl.bluebrain.nexus.admin.core.directives

import akka.http.scaladsl.model.headers.OAuth2BearerToken
import akka.http.scaladsl.model.{StatusCodes, Uri}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.testkit.ScalatestRouteTest
import ch.epfl.bluebrain.nexus.admin.core.Error
import ch.epfl.bluebrain.nexus.admin.core.Error._
import ch.epfl.bluebrain.nexus.admin.core.directives.AuthenticateDirectives._
import ch.epfl.bluebrain.nexus.admin.core.rejections.CommonRejections.DownstreamServiceError
import ch.epfl.bluebrain.nexus.admin.core.routes.RejectionHandling
import ch.epfl.bluebrain.nexus.commons.http.ContextUri
import ch.epfl.bluebrain.nexus.commons.http.JsonLdCirceSupport._
import ch.epfl.bluebrain.nexus.commons.iam.IamClient
import ch.epfl.bluebrain.nexus.commons.iam.auth.{AuthenticatedUser, User}
import ch.epfl.bluebrain.nexus.commons.iam.identity.Caller.{AnonymousCaller, AuthenticatedCaller}
import ch.epfl.bluebrain.nexus.commons.iam.identity.Identity.{GroupRef, UserRef}
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
import org.scalatest.{BeforeAndAfter, Matchers, WordSpecLike}

import scala.concurrent.Future
import scala.concurrent.duration._

class AuthenticateDirectivesSpec
    extends WordSpecLike
    with ScalatestRouteTest
    with Matchers
    with ScalaFutures
    with Randomness
    with MockitoSugar
    with BeforeAndAfter {

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

  before {
    Mockito.reset(cl)
  }

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(3 seconds, 100 milliseconds)

  "An AuthorizationDirectives" should {
    val ValidCredentials = OAuth2BearerToken("validToken")

    val group1 = GroupRef(IdentityId("localhost:8080/v0/realms/BBP/groups/group1"))
    val group2 = GroupRef(IdentityId("localhost:8080/v0/realms/BBP/groups/group2"))
    val user: User = AuthenticatedUser(
      Set(
        group1,
        group2,
        UserRef(IdentityId("localhost:8080/v0/realms/realm/users/f:someUUID:username"))
      ))
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
  }
}

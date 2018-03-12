package ch.epfl.bluebrain.nexus.admin.core.directives

import akka.http.scaladsl.model.headers.OAuth2BearerToken
import akka.http.scaladsl.model.{StatusCodes, Uri}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.testkit.ScalatestRouteTest
import ch.epfl.bluebrain.nexus.admin.core.Error
import ch.epfl.bluebrain.nexus.admin.core.Error._
import ch.epfl.bluebrain.nexus.admin.core.directives.AuthorizeDirective._
import ch.epfl.bluebrain.nexus.admin.core.routes.{ExceptionHandling, RejectionHandling}
import ch.epfl.bluebrain.nexus.admin.refined.permissions._
import ch.epfl.bluebrain.nexus.commons.http.ContextUri
import ch.epfl.bluebrain.nexus.commons.http.JsonLdCirceSupport._
import ch.epfl.bluebrain.nexus.commons.iam.IamClient
import ch.epfl.bluebrain.nexus.commons.iam.acls.{FullAccessControlList, Path, Permission, Permissions}
import ch.epfl.bluebrain.nexus.commons.iam.identity.Identity.{Anonymous, GroupRef}
import ch.epfl.bluebrain.nexus.commons.iam.identity.IdentityId
import ch.epfl.bluebrain.nexus.commons.test.Randomness
import ch.epfl.bluebrain.nexus.commons.types.HttpRejection.UnauthorizedAccess
import eu.timepit.refined.api.Validate
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.auto._
import org.mockito.Mockito
import org.mockito.Mockito._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{BeforeAndAfter, Matchers, WordSpecLike}

import scala.concurrent.Future
import scala.concurrent.duration._

class AuthorizeDirectiveSpec
    extends WordSpecLike
    with ScalatestRouteTest
    with Matchers
    with ScalaFutures
    with Randomness
    with MockitoSugar
    with BeforeAndAfter {

  private val authorize: AuthorizeDirective[Future] = fromFuture
  private implicit val config: Configuration   = Configuration.default.withDiscriminator("type")
  private val ErrorContext                     = ContextUri(Uri("http://nexus.example.com/contexts/nexus/core/error/v0.1.0"))
  private implicit val cl: IamClient[Future]   = mock[IamClient[Future]]

  private def route[A](resource: Path)(implicit cred: Option[OAuth2BearerToken], V: Validate[Permissions, A]) =
    (handleExceptions(ExceptionHandling.exceptionHandler(ErrorContext)) & handleRejections(
      RejectionHandling.rejectionHandler(ErrorContext))) {
      (get & authorize[A](resource.toString())) { f =>
        onSuccess(f) { _ =>
          complete("Success")
        }
      }
    }

  before {
    Mockito.reset(cl)
  }

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(3 seconds, 100 milliseconds)

  "An AuthorizationDirectives" should {
    val ValidCredentials = OAuth2BearerToken("validToken")

    val group1 = GroupRef(IdentityId("localhost:8080/v0/realms/BBP/groups/group1"))

    val acl = FullAccessControlList((group1, Path("projects/proj"), Permissions(Permission("projects/read"))))

    "return unauthorized when the requested path does not contains read permission" in {
      implicit val cred: Option[OAuth2BearerToken] = Some(ValidCredentials)
      val path                                     = "projects/proj/config"
      when(cl.getAcls(Path(path), parents = true, self = true)).thenReturn(Future.successful(FullAccessControlList()))

      Get(path) ~> route[ReadProjects](Path(path)) ~> check {
        status shouldEqual StatusCodes.Unauthorized
        responseAs[Error].code shouldEqual classNameOf[UnauthorizedAccess.type]
      }
    }

    "return unauthorized when the token on the cred is wrong" in {
      implicit val cred: Option[OAuth2BearerToken] = Some(OAuth2BearerToken("Invalid"))
      val path                                     = "projects/proj"
      when(cl.getAcls(Path(path), parents = true, self = true)).thenReturn(Future.failed(UnauthorizedAccess))

      Get(path) ~> route[ReadProjects](Path(path)) ~> check {
        status shouldEqual StatusCodes.Unauthorized
        responseAs[Error].code shouldEqual classNameOf[UnauthorizedAccess.type]
      }
    }

    "return authorized when the requested path for the authenticated cred contains read permission" in {
      implicit val cred: Option[OAuth2BearerToken] = Some(ValidCredentials)
      val path                                     = "projects/proj"
      when(cl.getAcls(Path(path), parents = true, self = true)).thenReturn(Future.successful(acl))

      Get(path) ~> route[ReadProjects](Path(path)) ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[String] shouldEqual "Success"
      }
    }

    "return authorized when the requested path for the anon cred contains write permission" in {
      implicit val cred: Option[OAuth2BearerToken] = None
      val path                                     = "/projects/test"
      val acl = FullAccessControlList(
        (Anonymous(), Path("projects/test"), Permissions(Permission("projects/own"), Permission("projects/write"))))
      when(cl.getAcls(Path(path), parents = true, self = true)).thenReturn(Future.successful(acl))

      Get(path) ~> route[WriteProjects](Path(path)) ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[String] shouldEqual "Success"
      }
    }
  }
}

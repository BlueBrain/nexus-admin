package ch.epfl.bluebrain.nexus.admin.directives

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.OAuth2BearerToken
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.ScalatestRouteTest
import ch.epfl.bluebrain.nexus.admin.CommonRejection.DownstreamServiceError
import ch.epfl.bluebrain.nexus.admin.Error
import ch.epfl.bluebrain.nexus.admin.Error._
import ch.epfl.bluebrain.nexus.admin.config.Contexts.errorCtxUri
import ch.epfl.bluebrain.nexus.admin.marshallers.instances._
import ch.epfl.bluebrain.nexus.admin.routes.{ExceptionHandling, RejectionHandling}
import ch.epfl.bluebrain.nexus.commons.types.HttpRejection.UnauthorizedAccess
import ch.epfl.bluebrain.nexus.iam.client.IamClient
import ch.epfl.bluebrain.nexus.iam.client.types.Identity._
import ch.epfl.bluebrain.nexus.iam.client.types.{AuthToken, Caller, Permission}
import ch.epfl.bluebrain.nexus.rdf.Iri.Path
import ch.epfl.bluebrain.nexus.rdf.Iri.Path._
import monix.eval.Task
import monix.execution.Scheduler.global
import org.mockito.integrations.scalatest.IdiomaticMockitoFixture
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{Matchers, WordSpecLike}

class AuthDirectivesSpec
    extends WordSpecLike
    with ScalatestRouteTest
    with Matchers
    with ScalaFutures
    with IdiomaticMockitoFixture {

  private val iamClient  = mock[IamClient[Task]]
  private val directives = new AuthDirectives(iamClient)(global) {}

  private val token   = Some(AuthToken("token"))
  private val cred    = OAuth2BearerToken("token")
  private val subject = User("alice", "realm")
  private val caller  = Caller(subject, Set(Group("nexus", "bbp")))

  private val path       = "org" / "proj"
  private val path2      = "org2" / "proj2"
  private val permission = Permission.unsafe("write")

  private def authorizeOnRoute(path: Path, permission: Permission)(implicit cred: Option[AuthToken]): Route =
    handleExceptions(ExceptionHandling.handler) {
      handleRejections(RejectionHandling.handler) {
        (get & directives.authorizeOn(path, permission)) {
          complete(StatusCodes.Accepted)
        }
      }
    }

  private def authCaller(caller: Caller)(implicit cred: Option[AuthToken]): Route =
    handleExceptions(ExceptionHandling.handler) {
      handleRejections(RejectionHandling.handler) {
        (get & directives.authCaller) { subject =>
          subject shouldEqual caller.subject
          complete(StatusCodes.Accepted)
        }
      }
    }

  "Authorization directives" should {

    "return the caller" in {
      iamClient.getCaller(token) shouldReturn Task(caller)
      Get("/") ~> addCredentials(cred) ~> authCaller(caller)(token) ~> check {
        status shouldEqual StatusCodes.Accepted
      }

      iamClient.getCaller(None) shouldReturn Task(Caller.anonymous)
      Get("/") ~> authCaller(Caller.anonymous)(None) ~> check {
        status shouldEqual StatusCodes.Accepted
      }
    }

    "authorize on a path" in {
      iamClient.authorizeOn(path, permission)(token) shouldReturn Task.unit
      Get("/") ~> addCredentials(cred) ~> authorizeOnRoute(path, permission)(token) ~> check {
        status shouldEqual StatusCodes.Accepted
      }

      iamClient.authorizeOn(path, permission)(None) shouldReturn Task.raiseError(UnauthorizedAccess)
      Get("/") ~> authorizeOnRoute(path, permission)(None) ~> check {
        status shouldEqual StatusCodes.Unauthorized
        responseAs[Error] shouldEqual Error(classNameOf[UnauthorizedAccess.type], None, errorCtxUri.asString)
      }

      iamClient.authorizeOn(path2, permission)(None) shouldReturn Task.raiseError(new RuntimeException)
      Get("/") ~> authorizeOnRoute(path2, permission)(None) ~> check {
        status shouldEqual StatusCodes.InternalServerError
        responseAs[Error] shouldEqual Error(classNameOf[DownstreamServiceError.type],
                                            Some("error while authenticating on the downstream service"),
                                            errorCtxUri.asString)
      }
    }
  }

}

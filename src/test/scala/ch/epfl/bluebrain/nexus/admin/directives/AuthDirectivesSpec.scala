package ch.epfl.bluebrain.nexus.admin.directives

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.OAuth2BearerToken
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.ScalatestRouteTest
import ch.epfl.bluebrain.nexus.admin.{Error, ExpectedException}
import ch.epfl.bluebrain.nexus.admin.Error._
import ch.epfl.bluebrain.nexus.admin.config.AppConfig.HttpConfig
import ch.epfl.bluebrain.nexus.admin.config.{AppConfig, Settings}
import ch.epfl.bluebrain.nexus.admin.exceptions.AdminError
import ch.epfl.bluebrain.nexus.admin.marshallers.instances._
import ch.epfl.bluebrain.nexus.admin.routes.Routes
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

  private val appConfig: AppConfig      = Settings(system).appConfig
  private implicit val http: HttpConfig = appConfig.http

  private val token   = Some(AuthToken("token"))
  private val cred    = OAuth2BearerToken("token")
  private val subject = User("alice", "realm")
  private val caller  = Caller(subject, Set(Group("nexus", "bbp")))

  private val path       = "org" / "proj"
  private val path2      = "org2" / "proj2"
  private val permission = Permission.unsafe("write")

  private def authorizeOnRoute(path: Path, permission: Permission)(implicit cred: Option[AuthToken]): Route =
    Routes.wrap(
      (get & directives.authorizeOn(path, permission)) {
        complete(StatusCodes.Accepted)
      }
    )

  private def authCaller(caller: Caller)(implicit cred: Option[AuthToken]): Route =
    Routes.wrap(
      (get & directives.extractSubject) { subject =>
        subject shouldEqual caller.subject
        complete(StatusCodes.Accepted)
      }
    )

  "Authorization directives" should {

    "return the caller" in {
      iamClient.identities(token) shouldReturn Task(caller)
      Get("/") ~> addCredentials(cred) ~> authCaller(caller)(token) ~> check {
        status shouldEqual StatusCodes.Accepted
      }

      iamClient.identities(None) shouldReturn Task(Caller.anonymous)
      Get("/") ~> authCaller(Caller.anonymous)(None) ~> check {
        status shouldEqual StatusCodes.Accepted
      }
    }

    "authorize on a path" in {
      iamClient.hasPermission(path, permission)(token) shouldReturn Task.pure(true)
      Get("/") ~> addCredentials(cred) ~> authorizeOnRoute(path, permission)(token) ~> check {
        status shouldEqual StatusCodes.Accepted
      }

      iamClient.hasPermission(path, permission)(None) shouldReturn Task.pure(false)
      Get("/") ~> authorizeOnRoute(path, permission)(None) ~> check {
        status shouldEqual StatusCodes.Forbidden
        responseAs[Error] shouldEqual Error("AuthorizationFailed",
                                            "The supplied authentication is not authorized to access this resource.")
      }

      iamClient.hasPermission(path2, permission)(None) shouldReturn Task.raiseError(ExpectedException)
      Get("/") ~> authorizeOnRoute(path2, permission)(None) ~> check {
        status shouldEqual StatusCodes.InternalServerError
        responseAs[Error] shouldEqual Error(classNameOf[AdminError.InternalError.type],
                                            "The system experienced an unexpected error, please try again later.")
      }
    }
  }

}

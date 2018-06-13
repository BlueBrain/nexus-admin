package ch.epfl.bluebrain.nexus.admin.service.directives

import akka.http.scaladsl.model.{StatusCodes, Uri}
import akka.http.scaladsl.server
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.testkit.ScalatestRouteTest
import cats.syntax.show._
import ch.epfl.bluebrain.nexus.admin.core.CommonRejections.IllegalParam
import ch.epfl.bluebrain.nexus.admin.core.Error
import ch.epfl.bluebrain.nexus.admin.core.Error._
import ch.epfl.bluebrain.nexus.admin.refined.organization.OrganizationReference
import ch.epfl.bluebrain.nexus.admin.service.directives.RefinedDirectives.{segment, projectReference, of => ofType}
import ch.epfl.bluebrain.nexus.admin.service.handlers.{ExceptionHandling, RejectionHandling}
import ch.epfl.bluebrain.nexus.commons.http.ContextUri
import ch.epfl.bluebrain.nexus.commons.http.JsonLdCirceSupport._
import ch.epfl.bluebrain.nexus.commons.test.Randomness
import io.circe.generic.auto._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{Inspectors, Matchers, WordSpecLike}

import scala.concurrent.duration._

class RefinedDirectivesSpec
    extends WordSpecLike
    with ScalatestRouteTest
    with Matchers
    with ScalaFutures
    with Randomness
    with Inspectors {

  private val ErrorContext = ContextUri(Uri("http://nexus.example.com/contexts/nexus/core/error/v0.1.0"))

  private def handler(route: => server.Route) =
    (handleExceptions(ExceptionHandling.exceptionHandler(ErrorContext, OrderedKeys())) & handleRejections(
      RejectionHandling.rejectionHandler(ErrorContext, OrderedKeys())))(route)

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(3 seconds, 100 milliseconds)

  "A RefinedDirectives" should {

    "reject route when refined type cannot is not a correct segment" in {
      Get("/ABC;-") ~> handler(
        (segment(ofType[OrganizationReference]) & pathEndOrSingleSlash & get)(_ => complete("Success"))) ~> check {
        status shouldEqual StatusCodes.BadRequest
        responseAs[Error].code shouldEqual classNameOf[IllegalParam.type]
      }
    }

    "return properly when refined type is a correct segment" in {
      Get("/abc") ~> handler(
        (segment(ofType[OrganizationReference]) & pathEndOrSingleSlash & get)(ref => complete(ref.value))) ~> check {
        status shouldEqual StatusCodes.OK
      }
    }

    "return properly when refined type is a correct reference which consists of two segments" in {
      Get("/abc/def") ~> handler((projectReference & pathEndOrSingleSlash & get)(ref => complete(ref.show))) ~> check {
        status shouldEqual StatusCodes.OK
      }
    }

    "return properly when refined type is a correct reference which consists of one segment" in {
      Get("/abc") ~> handler(
        (segment(ofType[OrganizationReference]) & pathEndOrSingleSlash & get)(ref => complete(ref.value))) ~> check {
        status shouldEqual StatusCodes.OK
      }
    }
  }

}

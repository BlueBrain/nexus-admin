package ch.epfl.bluebrain.nexus.admin.service.routes

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.ScalatestRouteTest
import ch.epfl.bluebrain.nexus.admin.core.CommonRejections.MissingParameter
import ch.epfl.bluebrain.nexus.admin.core.Error.classNameOf
import ch.epfl.bluebrain.nexus.admin.core.{Error, TestHelper}
import ch.epfl.bluebrain.nexus.admin.core.resources.ResourceRejection.ResourceAlreadyExists
import ch.epfl.bluebrain.nexus.admin.ld.Const.{`@context`, `@id`, `@type`}
import io.circe.Json
import org.scalatest.{Matchers, WordSpecLike}
import org.scalatest.concurrent.ScalaFutures
import ch.epfl.bluebrain.nexus.commons.http.JsonLdCirceSupport._
import org.scalatest.mockito.MockitoSugar
import io.circe.generic.auto._
import ch.epfl.bluebrain.nexus.admin.core.resources.ResourceRejection._
import ch.epfl.bluebrain.nexus.admin.core.Error._
import ch.epfl.bluebrain.nexus.commons.http.RdfMediaTypes

class OrganizationRoutesSpec
    extends WordSpecLike
    with Matchers
    with ScalatestRouteTest
    with ScalaFutures
    with MockitoSugar
    with TestHelper
    with AdminRoutesTestHelper {

  lazy val route = OrganizationRoutes(organizations).routes

  "OrganizationRoutes" should {

    val id      = genOrgReference()
    val orgJson = genOrganizationValue()

    "create an organization" in {
      setUpIamCalls(id.value)

      Put(s"/orgs/${id.value}", orgJson) ~> addCredentials(cred) ~> route ~> check {
        status shouldEqual StatusCodes.Created
        responseAs[Json] shouldEqual Json.obj(
          `@context` -> Json.fromString(appConfig.prefixes.coreContext.toString()),
          `@id`      -> Json.fromString(s"http://127.0.0.1:8080/v1/orgs/${id.value}"),
          "_rev"     -> Json.fromLong(1L)
        )
      }
      val org = organizations.fetch(id).futureValue.get
      org.id.value shouldEqual id
      org.value shouldEqual orgJson

    }

    "reject the creation of an organization which already exists" in {
      Put(s"/orgs/${id.value}", orgJson) ~> addCredentials(cred) ~> route ~> check {
        status shouldEqual StatusCodes.Conflict
        responseAs[Error].code shouldEqual classNameOf[ResourceAlreadyExists.type]
      }
    }

    "return not found for missing project" in {
      val other = genOrgReference()
      setUpIamCalls(other.value)
      Get(s"/orgs/${other.value}") ~> addCredentials(cred) ~> route ~> check {
        status shouldEqual StatusCodes.NotFound
      }
    }

    "reject the deprecation of an organization with incorrect rev" in {
      Delete(s"/orgs/${id.value}?rev=10", orgJson) ~> addCredentials(cred) ~> route ~> check {
        status shouldEqual StatusCodes.Conflict
        responseAs[Error].code shouldEqual classNameOf[IncorrectRevisionProvided.type]
      }
    }

    "reject the deprecation of an organization without rev" in {
      Delete(s"/orgs/${id.value}", orgJson) ~> addCredentials(cred) ~> route ~> check {
        status shouldEqual StatusCodes.BadRequest
        responseAs[Error].code shouldEqual classNameOf[MissingParameter.type]
      }
    }

    "deprecate an organization" in {
      Delete(s"/orgs/${id.value}?rev=1") ~> addCredentials(cred) ~> route ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[Json] shouldEqual Json.obj(
          `@context` -> Json.fromString(appConfig.prefixes.coreContext.toString()),
          `@id`      -> Json.fromString(s"http://127.0.0.1:8080/v1/orgs/${id.value}"),
          "_rev"     -> Json.fromLong(2L)
        )
      }
      val org = organizations.fetch(id).futureValue.get
      org.id.value shouldEqual id
      org.deprecated shouldEqual true
    }

    "fetch latest revision of an organization" in {
      val uuid = organizations.fetch(id).futureValue.get.uuid
      Get(s"/orgs/${id.value}") ~> addCredentials(cred) ~> route ~> check {
        status shouldEqual StatusCodes.OK
        contentType shouldEqual RdfMediaTypes.`application/ld+json`.toContentType
        responseAs[Json] shouldEqual (orgJson deepMerge Json.obj(
          `@context`    -> Json.fromString(appConfig.prefixes.coreContext.toString()),
          `@id`         -> Json.fromString(s"http://127.0.0.1:8080/v1/orgs/${id.value}"),
          `@type`       -> Json.fromString("nxv:Organization"),
          "label"       -> Json.fromString(id.value),
          "_deprecated" -> Json.fromBoolean(true),
          "_rev"        -> Json.fromLong(2L),
          "_uuid"       -> Json.fromString(uuid)
        ))
      }
    }

    "fetch previous revision of an organization" in {
      val uuid = organizations.fetch(id).futureValue.get.uuid
      Get(s"/orgs/${id.value}?rev=1") ~> addCredentials(cred) ~> route ~> check {
        status shouldEqual StatusCodes.OK
        contentType shouldEqual RdfMediaTypes.`application/ld+json`.toContentType
        responseAs[Json] shouldEqual (orgJson deepMerge Json.obj(
          `@context`    -> Json.fromString(appConfig.prefixes.coreContext.toString()),
          `@id`         -> Json.fromString(s"http://127.0.0.1:8080/v1/orgs/${id.value}"),
          `@type`       -> Json.fromString("nxv:Organization"),
          "label"       -> Json.fromString(id.value),
          "_deprecated" -> Json.fromBoolean(false),
          "_rev"        -> Json.fromLong(1L),
          "_uuid"       -> Json.fromString(uuid)
        ))
      }
    }

  }

}

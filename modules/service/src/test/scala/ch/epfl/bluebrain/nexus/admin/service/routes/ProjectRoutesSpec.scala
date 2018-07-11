package ch.epfl.bluebrain.nexus.admin.service.routes

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.BasicHttpCredentials
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.testkit.ScalatestRouteTest
import cats.syntax.show._
import ch.epfl.bluebrain.nexus.admin.core.CommonRejections.{IllegalParam, MissingParameter}
import ch.epfl.bluebrain.nexus.admin.core.Error._
import ch.epfl.bluebrain.nexus.admin.core.config.AppConfig._
import ch.epfl.bluebrain.nexus.admin.core.resources.ResourceRejection._
import ch.epfl.bluebrain.nexus.admin.core.{Error, TestHelper}
import ch.epfl.bluebrain.nexus.admin.ld.Const._
import ch.epfl.bluebrain.nexus.admin.refined.project._
import ch.epfl.bluebrain.nexus.commons.http.JsonLdCirceSupport._
import ch.epfl.bluebrain.nexus.commons.http.syntax.circe._
import ch.epfl.bluebrain.nexus.commons.http.RdfMediaTypes
import ch.epfl.bluebrain.nexus.commons.types.HttpRejection.UnauthorizedAccess
import eu.timepit.refined.auto._
import io.circe.Json
import io.circe.generic.auto._
import io.circe.syntax._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{Matchers, WordSpecLike}

class ProjectRoutesSpec
    extends WordSpecLike
    with Matchers
    with ScalatestRouteTest
    with ScalaFutures
    with MockitoSugar
    with TestHelper
    with AdminRoutesTestHelper {

  val route = ProjectRoutes(projects).routes ~ ProjectAclRoutes(projects, proxy).routes

  "A ProjectRoutes" should {

    val reference      = genProjectReference()
    val projectValue   = genProjectValue()
    val json           = projectValue.asJson
    val orgValue: Json = genOrganizationValue()

    "create a project" in {
      setUpIamCalls(reference.show)
      organizations.create(reference.organizationReference, orgValue)(caller).futureValue

      Put(s"/projects/${reference.show}", json) ~> addCredentials(cred) ~> route ~> check {
        status shouldEqual StatusCodes.Created
        responseAs[Json] shouldEqual Json.obj(
          `@context` -> Json.fromString(appConfig.prefixes.coreContext.toString()),
          `@id`      -> Json.fromString(s"http://127.0.0.1:8080/v1/projects/${reference.show}"),
          "_rev"     -> Json.fromLong(1L)
        )
      }
      val created = projects.fetch(reference).futureValue.get
      created.id.value shouldEqual reference
      created.rev shouldEqual 1L
      created.uuid should fullyMatch regex """[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"""
      created.deprecated shouldEqual false
      created.value shouldEqual json
    }

    "reject the creation of a project without name" in {
      val other = genProjectReference()
      organizations.create(other.organizationReference, orgValue)(caller).futureValue
      setUpIamCalls(other.show)
      Put(s"/projects/${other.show}", genProjectValue().asJson.removeKeys("name")) ~> addCredentials(cred) ~> route ~> check {
        status shouldEqual StatusCodes.BadRequest
        responseAs[Error].code shouldEqual classNameOf[ShapeConstraintViolations.type]
      }
    }

    "reject the creation of a project which already exists" in {
      Put(s"/projects/${reference.show}", genProjectValue().asJson) ~> addCredentials(cred) ~> route ~> check {
        status shouldEqual StatusCodes.Conflict
        responseAs[Error].code shouldEqual classNameOf[ResourceAlreadyExists.type]
      }
    }

    "reject the creation of a project with wrong id" in {
      Put(s"/projects/someorg/123K=-", genProjectValue().asJson) ~> addCredentials(cred) ~> route ~> check {
        status shouldEqual StatusCodes.BadRequest
        responseAs[Error].code shouldEqual classNameOf[IllegalParam.type]
      }
    }

    "return not found for missing project" in {
      val other = genProjectReference()
      setUpIamCalls(other.show)
      Get(s"/projects/${other.show}") ~> addCredentials(cred) ~> route ~> check {
        status shouldEqual StatusCodes.NotFound
      }
    }

    "reject the deprecation of a project with incorrect rev" in {
      Delete(s"/projects/${reference.show}?rev=10", json) ~> addCredentials(cred) ~> route ~> check {
        status shouldEqual StatusCodes.Conflict
        responseAs[Error].code shouldEqual classNameOf[IncorrectRevisionProvided.type]
      }
    }

    "reject the deprecation of a project without rev" in {
      Delete(s"/projects/${reference.show}", json) ~> addCredentials(cred) ~> route ~> check {
        status shouldEqual StatusCodes.BadRequest
        responseAs[Error].code shouldEqual classNameOf[MissingParameter.type]
      }
    }

    "deprecate a project" in {
      Delete(s"/projects/${reference.show}?rev=1") ~> addCredentials(cred) ~> route ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[Json] shouldEqual Json.obj(
          `@context` -> Json.fromString(appConfig.prefixes.coreContext.toString()),
          `@id`      -> Json.fromString(s"http://127.0.0.1:8080/v1/projects/${reference.show}"),
          "_rev"     -> Json.fromLong(2L)
        )
      }
      val created = projects.fetch(reference).futureValue.get
      created.id.value shouldEqual reference
      created.rev shouldEqual 2L
      created.uuid should fullyMatch regex """[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"""
      created.deprecated shouldEqual true
      created.value shouldEqual json
    }

    "fetch latest revision of a project" in {
      val uuid = projects.fetch(reference).futureValue.get.uuid
      Get(s"/projects/${reference.show}") ~> addCredentials(cred) ~> route ~> check {
        status shouldEqual StatusCodes.OK
        contentType shouldEqual RdfMediaTypes.`application/ld+json`.toContentType
        responseAs[Json] shouldEqual (json deepMerge Json.obj(
          `@context`    -> Json.fromString(appConfig.prefixes.coreContext.toString()),
          `@id`         -> Json.fromString(s"http://127.0.0.1:8080/v1/projects/${reference.show}"),
          `@type`       -> Json.fromString("nxv:Project"),
          "label"       -> Json.fromString(reference.projectLabel),
          "_rev"        -> Json.fromLong(2L),
          "_deprecated" -> Json.fromBoolean(true),
          "_uuid"       -> Json.fromString(uuid)
        ))
      }
    }

    "fetch old revision of a project" in {
      val uuid = projects.fetch(reference).futureValue.get.uuid
      Get(s"/projects/${reference.show}?rev=1") ~> addCredentials(cred) ~> route ~> check {
        status shouldEqual StatusCodes.OK
        contentType shouldEqual RdfMediaTypes.`application/ld+json`.toContentType
        responseAs[Json] shouldEqual (json deepMerge Json.obj(
          `@context`    -> Json.fromString(appConfig.prefixes.coreContext.toString()),
          `@id`         -> Json.fromString(s"http://127.0.0.1:8080/v1/projects/${reference.show}"),
          `@type`       -> Json.fromString("nxv:Project"),
          "label"       -> Json.fromString(reference.projectLabel),
          "_rev"        -> Json.fromLong(1L),
          "_deprecated" -> Json.fromBoolean(false),
          "_uuid"       -> Json.fromString(uuid)
        ))
      }
    }

    "return not found for unknown revision of a project" in {
      Get(s"/projects/${reference.show}?rev=4") ~> addCredentials(cred) ~> route ~> check {
        status shouldEqual StatusCodes.NotFound
      }
    }

    "reject the deprecation of a project already deprecated" in {
      Delete(s"/projects/${reference.show}?rev=2", json) ~> addCredentials(cred) ~> route ~> check {
        status shouldEqual StatusCodes.BadRequest
        responseAs[Error].code shouldEqual classNameOf[ResourceIsDeprecated.type]
      }
    }

    "reject the deprecation of a project which does not exists" in {
      val other = genProjectReference()
      setUpIamCalls(other.show)
      Delete(s"/projects/${other.show}?rev=1", json) ~> addCredentials(cred) ~> route ~> check {
        status shouldEqual StatusCodes.NotFound
        responseAs[Error].code shouldEqual classNameOf[ResourceDoesNotExists.type]
      }
    }

    val refUpdate        = genProjectReference()
    val projectValUpdate = genProjectUpdate()
    val jsonUpdate       = projectValUpdate.asJson
    val orgUpdateValue   = genOrganizationValue()

    "update a project" in {
      setUpIamCalls(refUpdate.show)

      organizations.create(refUpdate.organizationReference, orgUpdateValue)(caller).futureValue

      Put(s"/projects/${refUpdate.show}", json) ~> addCredentials(cred) ~> route ~> check {
        status shouldEqual StatusCodes.Created
      }

      Put(s"/projects/${refUpdate.show}?rev=1", jsonUpdate) ~> addCredentials(cred) ~> route ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[Json] shouldEqual Json.obj(
          `@context` -> Json.fromString(appConfig.prefixes.coreContext.toString()),
          `@id`      -> Json.fromString(s"http://127.0.0.1:8080/v1/projects/${refUpdate.show}"),
          "_rev"     -> Json.fromLong(2L)
        )
      }
    }

    "reject the update of a project without name" in {
      val json = jsonUpdate.removeKeys("name")
      Put(s"/projects/${refUpdate.show}?rev=2", json) ~> addCredentials(cred) ~> route ~> check {
        status shouldEqual StatusCodes.BadRequest
        responseAs[Error].code shouldEqual classNameOf[ShapeConstraintViolations.type]
      }
    }

    "reject the update of a project which does not exists" in {
      val other               = genProjectReference()
      val otherOrgUpdateValue = genOrganizationValue()
      organizations.create(other.organizationReference, otherOrgUpdateValue)(caller).futureValue

      setUpIamCalls(other.show)
      Put(s"/projects/${other.show}?rev=1", json) ~> addCredentials(cred) ~> route ~> check {
        status shouldEqual StatusCodes.NotFound
        responseAs[Error].code shouldEqual classNameOf[ResourceDoesNotExists.type]
      }
    }

    "reject the update of a project with a wrong revision" in {
      Put(s"/projects/${refUpdate.show}?rev=4", json) ~> addCredentials(cred) ~> route ~> check {
        status shouldEqual StatusCodes.Conflict
        responseAs[Error].code shouldEqual classNameOf[IncorrectRevisionProvided.type]
      }
    }

    "reject the request when credentials are not in the form of Bearer Token" in {
      Get(s"/projects/") ~> addCredentials(BasicHttpCredentials("something")) ~> route ~> check {
        status shouldEqual StatusCodes.Unauthorized
        responseAs[Error].code shouldEqual classNameOf[UnauthorizedAccess.type]
      }
    }
  }
}

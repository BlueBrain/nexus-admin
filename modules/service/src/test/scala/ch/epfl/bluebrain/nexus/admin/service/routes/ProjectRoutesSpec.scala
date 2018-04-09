package ch.epfl.bluebrain.nexus.admin.service.routes

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.BasicHttpCredentials
import ch.epfl.bluebrain.nexus.admin.core.CommonRejections.{IllegalParam, IllegalPayload, MissingParameter}
import ch.epfl.bluebrain.nexus.admin.core.Error._
import ch.epfl.bluebrain.nexus.admin.core.config.AppConfig._
import ch.epfl.bluebrain.nexus.admin.core.projects.Project
import ch.epfl.bluebrain.nexus.admin.core.resources.ResourceRejection._
import ch.epfl.bluebrain.nexus.admin.core.types.Ref._
import ch.epfl.bluebrain.nexus.admin.core.{Error, TestHepler}
import ch.epfl.bluebrain.nexus.admin.ld.Const._
import ch.epfl.bluebrain.nexus.admin.refined.permissions.HasReadProjects
import ch.epfl.bluebrain.nexus.commons.http.JsonLdCirceSupport._
import ch.epfl.bluebrain.nexus.commons.http.JsonOps._
import ch.epfl.bluebrain.nexus.commons.http.RdfMediaTypes
import ch.epfl.bluebrain.nexus.commons.iam.acls.Permission.Read
import ch.epfl.bluebrain.nexus.commons.iam.acls.{Permission, Permissions}
import ch.epfl.bluebrain.nexus.commons.types.HttpRejection.UnauthorizedAccess
import eu.timepit.refined.api.RefType.{applyRef, refinedRefType}
import io.circe.Json
import io.circe.generic.auto._
import io.circe.syntax._
import org.scalatest.{Matchers, WordSpecLike}

class ProjectRoutesSpec extends WordSpecLike with Matchers with TestHepler with ProjectRoutesTestHelper {

  "A ProjectRoutes" should {

    implicit val hasRead: HasReadProjects =
      applyRef[HasReadProjects](Permissions(Read, Permission("projects/read"))).toPermTry.get

    val reference    = genReference()
    val projectValue = genProjectValue()
    val json         = projectValue.asJson

    "create a project" in {
      setUpIamCalls(reference.value)

      Put(s"/projects/${reference.value}", json) ~> addCredentials(cred) ~> route ~> check {
        status shouldEqual StatusCodes.Created
        responseAs[Json] shouldEqual Json.obj(
          `@context` -> Json.fromString(appConfig.prefixes.coreContext.toString()),
          `@id`      -> Json.fromString(s"http://127.0.0.1:8080/v1/projects/${reference.value}"),
          "nxv:rev"  -> Json.fromLong(1L)
        )
      }
      projects.fetch(reference).futureValue shouldEqual Some(
        Project(reference, 1L, projectValue, json, deprecated = false))
    }

    "reject the creation of a project without name" in {
      val other = genReference()
      setUpIamCalls(other.value)
      Put(s"/projects/${other.value}", genProjectValue().asJson.removeKeys("name")) ~> addCredentials(cred) ~> route ~> check {
        status shouldEqual StatusCodes.BadRequest
        responseAs[Error].code shouldEqual classNameOf[ShapeConstraintViolations.type]
      }
    }

    "reject the creation of a project which already exists" in {
      Put(s"/projects/${reference.value}", genProjectValue().asJson) ~> addCredentials(cred) ~> route ~> check {
        status shouldEqual StatusCodes.Conflict
        responseAs[Error].code shouldEqual classNameOf[ResourceAlreadyExists.type]
      }
    }

    "reject the creation of a project with wrong id" in {
      Put(s"/projects/123K=-", genProjectValue().asJson) ~> addCredentials(cred) ~> route ~> check {
        status shouldEqual StatusCodes.BadRequest
        responseAs[Error].code shouldEqual classNameOf[IllegalParam.type]
      }
    }

    "return not found for missing project" in {
      val other = genReference()
      setUpIamCalls(other.value)
      Get(s"/projects/${other.value}") ~> addCredentials(cred) ~> route ~> check {
        status shouldEqual StatusCodes.NotFound
      }
    }

    "reject the deprecation of a project with incorrect rev" in {
      Delete(s"/projects/${reference.value}?rev=10", json) ~> addCredentials(cred) ~> route ~> check {
        status shouldEqual StatusCodes.Conflict
        responseAs[Error].code shouldEqual classNameOf[IncorrectRevisionProvided.type]
      }
    }

    "reject the deprecation of a project without rev" in {
      Delete(s"/projects/${reference.value}", json) ~> addCredentials(cred) ~> route ~> check {
        status shouldEqual StatusCodes.BadRequest
        responseAs[Error].code shouldEqual classNameOf[MissingParameter.type]
      }
    }

    "deprecate a project" in {
      Delete(s"/projects/${reference.value}?rev=1") ~> addCredentials(cred) ~> route ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[Json] shouldEqual Json.obj(
          `@context` -> Json.fromString(appConfig.prefixes.coreContext.toString()),
          `@id`      -> Json.fromString(s"http://127.0.0.1:8080/v1/projects/${reference.value}"),
          "nxv:rev"  -> Json.fromLong(2L)
        )
      }
      projects.fetch(reference).futureValue shouldEqual Some(
        Project(reference, 2L, projectValue, json, deprecated = true))
    }

    "fetch latest revision of a project" in {
      Get(s"/projects/${reference.value}") ~> addCredentials(cred) ~> route ~> check {
        status shouldEqual StatusCodes.OK
        contentType shouldEqual RdfMediaTypes.`application/ld+json`.toContentType
        responseAs[Json] shouldEqual (json deepMerge Json.obj(
          `@context`       -> Json.fromString(appConfig.prefixes.coreContext.toString()),
          `@id`            -> Json.fromString(s"http://127.0.0.1:8080/v1/projects/${reference.value}"),
          `@type`          -> Json.fromString("nxv:Project"),
          "nxv:rev"        -> Json.fromLong(2L),
          "nxv:deprecated" -> Json.fromBoolean(true)
        ))
      }
    }

    "fetch old revision of a project" in {
      Get(s"/projects/${reference.value}?rev=1") ~> addCredentials(cred) ~> route ~> check {
        status shouldEqual StatusCodes.OK
        contentType shouldEqual RdfMediaTypes.`application/ld+json`.toContentType
        responseAs[Json] shouldEqual (json deepMerge Json.obj(
          `@context`       -> Json.fromString(appConfig.prefixes.coreContext.toString()),
          `@id`            -> Json.fromString(s"http://127.0.0.1:8080/v1/projects/${reference.value}"),
          `@type`          -> Json.fromString("nxv:Project"),
          "nxv:rev"        -> Json.fromLong(1L),
          "nxv:deprecated" -> Json.fromBoolean(false)
        ))
      }
    }

    "return not found for unknown revision of a project" in {
      Get(s"/projects/${reference.value}?rev=4") ~> addCredentials(cred) ~> route ~> check {
        status shouldEqual StatusCodes.NotFound
      }
    }

    "reject the deprecation of a project already deprecated" in {
      Delete(s"/projects/${reference.value}?rev=2", json) ~> addCredentials(cred) ~> route ~> check {
        status shouldEqual StatusCodes.BadRequest
        responseAs[Error].code shouldEqual classNameOf[ResourceIsDeprecated.type]
      }
    }

    "reject the deprecation of a project which does not exists" in {
      val other = genReference()
      setUpIamCalls(other.value)
      Delete(s"/projects/${other.value}?rev=1", json) ~> addCredentials(cred) ~> route ~> check {
        status shouldEqual StatusCodes.NotFound
        responseAs[Error].code shouldEqual classNameOf[ResourceDoesNotExists.type]
      }
    }

    val refUpdate        = genReference()
    val projectValUpdate = genProjectUpdate()
    val jsonUpdate       = projectValUpdate.asJson

    "update a project" in {
      setUpIamCalls(refUpdate.value)

      Put(s"/projects/${refUpdate.value}", json) ~> addCredentials(cred) ~> route ~> check {
        status shouldEqual StatusCodes.Created
      }

      Put(s"/projects/${refUpdate.value}?rev=1", jsonUpdate) ~> addCredentials(cred) ~> route ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[Json] shouldEqual Json.obj(
          `@context` -> Json.fromString(appConfig.prefixes.coreContext.toString()),
          `@id`      -> Json.fromString(s"http://127.0.0.1:8080/v1/projects/${refUpdate.value}"),
          "nxv:rev"  -> Json.fromLong(2L)
        )
      }
    }

    "reject the update of a project without name" in {
      val json = jsonUpdate deepMerge Json.obj("config" -> Json.obj("maxAttachmentSize" -> Json.fromString("one")))
      Put(s"/projects/${refUpdate.value}?rev=2", json) ~> addCredentials(cred) ~> route ~> check {
        status shouldEqual StatusCodes.BadRequest
        responseAs[Error].code shouldEqual classNameOf[ShapeConstraintViolations.type]
      }
    }

    "reject the update of a project which contains the same prefix on the prefixMappings payload as the existing project" in {
      val invalidUpdate = genProjectValue(randomProjectPrefix(), randomProjectPrefix()).asJson
      Put(s"/projects/${refUpdate.value}?rev=2", invalidUpdate) ~> addCredentials(cred) ~> route ~> check {
        status shouldEqual StatusCodes.BadRequest
        responseAs[Error].code shouldEqual classNameOf[IllegalPayload.type]
      }
    }

    "reject the update of a project which does not exists" in {
      val other = genReference()
      setUpIamCalls(other.value)
      Put(s"/projects/${other.value}?rev=1", json) ~> addCredentials(cred) ~> route ~> check {
        status shouldEqual StatusCodes.NotFound
        responseAs[Error].code shouldEqual classNameOf[ResourceDoesNotExists.type]
      }
    }

    "reject the update of a project with a wrong revision" in {
      Put(s"/projects/${refUpdate.value}?rev=4", json) ~> addCredentials(cred) ~> route ~> check {
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

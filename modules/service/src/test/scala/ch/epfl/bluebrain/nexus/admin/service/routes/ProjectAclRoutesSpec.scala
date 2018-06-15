package ch.epfl.bluebrain.nexus.admin.service.routes

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.Authorization
import cats.syntax.show._
import ch.epfl.bluebrain.nexus.admin.core.Error.classNameOf
import ch.epfl.bluebrain.nexus.admin.core.resources.ResourceRejection.ResourceDoesNotExists
import ch.epfl.bluebrain.nexus.admin.core.{Error, TestHelper}
import ch.epfl.bluebrain.nexus.admin.ld.Const.{`@context`, `@id`}
import ch.epfl.bluebrain.nexus.admin.refined.project.ProjectReference
import ch.epfl.bluebrain.nexus.commons.http.JsonLdCirceSupport._
import akka.http.scaladsl.server.Directives._
import eu.timepit.refined.auto._
import io.circe.Json
import io.circe.generic.auto._
import io.circe.syntax._
import org.scalatest.{Matchers, WordSpecLike}
import ch.epfl.bluebrain.nexus.admin.refined.project._

class ProjectAclRoutesSpec extends WordSpecLike with Matchers with AdminRoutesTestHelper with TestHelper {

  val route = ProjectRoutes(projects).routes ~ ProjectAclRoutes(projects, proxy).routes

  "A ProjectAclRoutes" should {

    val proj: ProjectReference = ProjectReference("org", "proj")
    val projectValue           = genProjectValue()
    val json                   = projectValue.asJson
    val orgValue: Json         = genOrganizationValue()

    "reject when project does not exists" in {
      setUpIamCalls(proj.show)
      organizations.create(proj.organizationReference, orgValue)(caller).futureValue

      Get(s"/projects/${proj.show}/acls") ~> addCredentials(cred) ~> route ~> check {
        status shouldEqual StatusCodes.NotFound
        responseAs[Error].code shouldEqual classNameOf[ResourceDoesNotExists.type]
      }
    }

    "create a project" in {
      Put(s"/projects/${proj.show}", json) ~> addCredentials(cred) ~> route ~> check {
        status shouldEqual StatusCodes.Created
        responseAs[Json] shouldEqual Json.obj(
          `@context` -> Json.fromString(appConfig.prefixes.coreContext.toString()),
          `@id`      -> Json.fromString(s"http://127.0.0.1:8080/v1/projects/${proj.show}"),
          "_rev"     -> Json.fromLong(1L)
        )
      }
    }

    "proxy the request to iam (GET method)" in {
      Get(s"/projects/${proj.show}/acls?self=true") ~> addCredentials(cred) ~> route ~> check {
        status shouldEqual StatusCodes.OK
        responseEntity.isKnownEmpty() shouldEqual true
        header("Authorization") shouldEqual Some(Authorization(cred))
      }
    }

    "proxy the request to iam (PUT method)" in {
      Put(s"/projects/${proj.show}/acls", json) ~> addCredentials(cred) ~> route ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[Json] shouldEqual json
      }
    }
  }
}

package ch.epfl.bluebrain.nexus.admin.service.routes

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.OAuth2BearerToken
import akka.http.scaladsl.testkit.ScalatestRouteTest
import cats.instances.future._
import ch.epfl.bluebrain.nexus.admin.core.Error._
import ch.epfl.bluebrain.nexus.admin.core.config.AppConfig._
import ch.epfl.bluebrain.nexus.admin.core.config.{AppConfig, Settings}
import ch.epfl.bluebrain.nexus.admin.core.projects.{Project, Projects}
import ch.epfl.bluebrain.nexus.admin.service.rejections.CommonRejections.IllegalParam
import ch.epfl.bluebrain.nexus.admin.core.resources.ResourceRejection._
import ch.epfl.bluebrain.nexus.admin.core.resources.ResourceState.{Initial, eval, next}
import ch.epfl.bluebrain.nexus.admin.core.types.Ref._
import ch.epfl.bluebrain.nexus.admin.core.{Error, TestHepler}
import ch.epfl.bluebrain.nexus.admin.ld.Const._
import ch.epfl.bluebrain.nexus.admin.refined.permissions.HasReadProjects
import ch.epfl.bluebrain.nexus.commons.http.JsonLdCirceSupport._
import ch.epfl.bluebrain.nexus.commons.http.RdfMediaTypes
import ch.epfl.bluebrain.nexus.commons.iam.IamClient
import ch.epfl.bluebrain.nexus.commons.iam.acls.Permission.Read
import ch.epfl.bluebrain.nexus.commons.iam.acls.{FullAccessControlList, Path, Permission, Permissions}
import ch.epfl.bluebrain.nexus.commons.iam.identity.Caller.AnonymousCaller
import ch.epfl.bluebrain.nexus.commons.iam.identity.Identity.Anonymous
import ch.epfl.bluebrain.nexus.sourcing.mem.MemoryAggregate
import ch.epfl.bluebrain.nexus.sourcing.mem.MemoryAggregate._
import com.typesafe.config.ConfigFactory
import eu.timepit.refined.api.RefType.{applyRef, refinedRefType}
import io.circe.Json
import io.circe.generic.auto._
import io.circe.syntax._
import org.mockito.Mockito._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{Matchers, WordSpecLike}

import scala.concurrent.Future
import scala.concurrent.duration._

class ProjectRoutesSpec
    extends WordSpecLike
    with Matchers
    with ScalatestRouteTest
    with ScalaFutures
    with MockitoSugar
    with TestHepler {

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(6 seconds, 300 milliseconds)

  private val valid                         = ConfigFactory.parseResources("test-app.conf").resolve()
  private implicit val appConfig: AppConfig = new Settings(valid).appConfig

  private val caller: AnonymousCaller = AnonymousCaller(Anonymous())
  private val cred: OAuth2BearerToken = OAuth2BearerToken("validToken")

  private implicit val optCred: Option[OAuth2BearerToken] = Some(cred)
  private implicit val cl: IamClient[Future]              = mock[IamClient[Future]]
  private val acl = FullAccessControlList(
    (Anonymous(),
     Path("projects/proj"),
     Permissions(Permission("projects/read"), Permission("projects/create"), Permission("projects/write"))))

  private val aggProject = MemoryAggregate("projects")(Initial, next, eval).toF[Future]
  private val projects   = Projects(aggProject)
  private val route      = ProjectRoutes(projects).routes

  private def setUpIamCalls(path: String) = {
    when(cl.getCaller(optCred, filterGroups = true)).thenReturn(Future.successful(caller))
    when(cl.getAcls(Path(path), parents = true, self = true)).thenReturn(Future.successful(acl))
  }

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
    val projectValUpdate = genProjectValue()
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

  }
}

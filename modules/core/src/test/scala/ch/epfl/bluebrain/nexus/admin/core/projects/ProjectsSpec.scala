package ch.epfl.bluebrain.nexus.admin.core.projects

import cats.instances.try_._
import ch.epfl.bluebrain.nexus.admin.core.CallerCtx._
import ch.epfl.bluebrain.nexus.admin.core.CommonRejections.IllegalPayload
import ch.epfl.bluebrain.nexus.admin.core.Fault.CommandRejected
import ch.epfl.bluebrain.nexus.admin.core.TestHepler
import ch.epfl.bluebrain.nexus.admin.core.config.AppConfig.ProjectsConfig
import ch.epfl.bluebrain.nexus.admin.core.projects.Project.ProjectValue
import ch.epfl.bluebrain.nexus.admin.core.projects.Projects.EvalProject
import ch.epfl.bluebrain.nexus.admin.core.projects.ProjectsSpec._
import ch.epfl.bluebrain.nexus.admin.core.resources.ResourceRejection
import ch.epfl.bluebrain.nexus.admin.core.resources.ResourceRejection._
import ch.epfl.bluebrain.nexus.admin.core.resources.ResourceState._
import ch.epfl.bluebrain.nexus.admin.core.types.Ref._
import ch.epfl.bluebrain.nexus.admin.core.types.RefVersioned
import ch.epfl.bluebrain.nexus.admin.refined.permissions._
import ch.epfl.bluebrain.nexus.admin.refined.project._
import ch.epfl.bluebrain.nexus.commons.http.JsonOps._
import ch.epfl.bluebrain.nexus.commons.iam.acls.Permission._
import ch.epfl.bluebrain.nexus.commons.iam.acls.{Permission, Permissions}
import ch.epfl.bluebrain.nexus.commons.iam.identity.Caller.AnonymousCaller
import ch.epfl.bluebrain.nexus.commons.iam.identity.Identity.Anonymous
import ch.epfl.bluebrain.nexus.commons.shacl.validator.{ImportResolver, ShaclSchema, ShaclValidator}
import ch.epfl.bluebrain.nexus.sourcing.mem.MemoryAggregate
import ch.epfl.bluebrain.nexus.sourcing.mem.MemoryAggregate._
import eu.timepit.refined.api.RefType.{applyRef, refinedRefType}
import eu.timepit.refined.auto._
import io.circe.Json
import io.circe.syntax._
import org.scalatest.{CancelAfterFailure, Matchers, TryValues, WordSpecLike}

import scala.concurrent.duration._
import scala.util.Try

class ProjectsSpec extends WordSpecLike with Matchers with TryValues with TestHepler with CancelAfterFailure {

  private implicit val caller: AnonymousCaller = AnonymousCaller(Anonymous())
  private implicit val config: ProjectsConfig =
    ProjectsConfig(3 seconds, "https://nexus.example.ch/v1/projects/", 100000L)
  private val aggProject = MemoryAggregate("projects")(Initial, next, EvalProject().apply).toF[Try]
  private val projects   = Projects(aggProject)

  trait Context {
    val id: ProjectReference = genReference()
    val value: ProjectValue  = genProjectValue()
  }

  "A Project bundle" should {
    implicit val hasWrite: HasWriteProjects =
      applyRef[HasWriteProjects](Permissions(Read, Permission("projects/write"))).toPermTry.success.value
    implicit val hasOwn: HasCreateProjects =
      applyRef[HasCreateProjects](Permissions(Read, Permission("projects/create"))).toPermTry.success.value
    implicit val hasRead: HasReadProjects =
      applyRef[HasReadProjects](Permissions(Read, Permission("projects/read"))).toPermTry.success.value

    "create a new project" in new Context {
      projects.create(id, value.asJson).success.value shouldEqual RefVersioned(id, 1L)
      projects.fetch(id).success.value shouldEqual Some(Project(id, 1L, value, value.asJson, deprecated = false))
    }

    "prevent creating a project without a name" in new Context {
      val rej =
        projects.create(id, value.asJson.removeKeys("name")).failure.exception.asInstanceOf[CommandRejected].rejection
      rej shouldBe a[ResourceRejection.ShapeConstraintViolations]
    }

    "prevent updating a project that overrides prefixMappings with other values" in new Context {
      val updatedValue: ProjectValue = genProjectValue(nxvPrefix = randomProjectPrefix())
      projects.create(id, value.asJson).success.value shouldEqual RefVersioned(id, 1L)
      projects.update(id, 1L, updatedValue.asJson).failure.exception shouldEqual CommandRejected(WrappedRejection(
        IllegalPayload("Invalid 'prefixMappings' object",
                       Some("The 'prefixMappings' values cannot be overridden but just new values can be appended."))))
    }

    "prevent updating a project with maxAttachmentSize as a string" in new Context {
      val updatedValue: ProjectValue = genProjectUpdate()
      val updatedJson = updatedValue.asJson deepMerge Json.obj(
        "config" -> Json.obj("maxAttachmentSize" -> Json.fromString("one")))
      projects.create(id, value.asJson).success.value shouldEqual RefVersioned(id, 1L)
      val rej = projects.update(id, 1L, updatedJson).failure.exception.asInstanceOf[CommandRejected].rejection
      rej shouldBe a[ResourceRejection.ShapeConstraintViolations]
    }

    "update a project" in new Context {
      val updatedValue: ProjectValue = genProjectUpdate()
      projects.create(id, value.asJson).success.value shouldEqual RefVersioned(id, 1L)
      projects.update(id, 1L, updatedValue.asJson).success.value shouldEqual RefVersioned(id, 2L)
      projects.fetch(id).success.value shouldEqual Some(
        Project(id, 2L, updatedValue, updatedValue.asJson, deprecated = false))
    }

    "deprecate a project" in new Context {
      projects.create(id, value.asJson).success.value shouldEqual RefVersioned(id, 1L)
      projects.deprecate(id, 1L).success.value shouldEqual RefVersioned(id, 2L)
      projects.fetch(id).success.value shouldEqual Some(Project(id, 2L, value, value.asJson, deprecated = true))
    }

    "fetch old revision of a project" in new Context {
      val updatedValue: ProjectValue = genProjectUpdate()
      projects.create(id, value.asJson).success.value shouldEqual RefVersioned(id, 1L)
      projects.update(id, 1L, updatedValue.asJson).success.value shouldEqual RefVersioned(id, 2L)

      projects.fetch(id, 2L).success.value shouldEqual Some(
        Project(id, 2L, updatedValue, updatedValue.asJson, deprecated = false))
      projects.fetch(id, 1L).success.value shouldEqual Some(Project(id, 1L, value, value.asJson, deprecated = false))

    }

    "return None when fetching a revision that does not exist" in new Context {
      projects.create(id, value.asJson).success.value shouldEqual RefVersioned(id, 1L)
      projects.fetch(id, 10L).success.value shouldEqual None
    }

    "prevent double deprecations" in new Context {
      projects.create(id, value.asJson).success.value shouldEqual RefVersioned(id, 1L)
      projects.deprecate(id, 1L).success.value shouldEqual RefVersioned(id, 2L)
      projects.deprecate(id, 2L).failure.exception shouldEqual CommandRejected(ResourceIsDeprecated)
    }

    "prevent updating when deprecated" in new Context {
      val updatedValue: ProjectValue = genProjectValue()
      projects.create(id, value.asJson).success.value shouldEqual RefVersioned(id, 1L)
      projects.deprecate(id, 1L).success.value shouldEqual RefVersioned(id, 2L)
      projects.update(id, 2L, updatedValue.asJson).failure.exception shouldEqual CommandRejected(ResourceIsDeprecated)
    }

    "prevent updating to non existing project" in new Context {
      projects.create(id, value.asJson).success.value shouldEqual RefVersioned(id, 1L)
      projects.update(genReference(), 2L, value.asJson).failure.exception shouldEqual CommandRejected(
        ResourceDoesNotExists)
    }

    "prevent updating with incorrect rev" in new Context {
      val updatedValue: ProjectValue = genProjectValue()
      projects.create(id, value.asJson).success.value shouldEqual RefVersioned(id, 1L)
      projects.update(id, 2L, updatedValue.asJson).failure.exception shouldEqual CommandRejected(
        IncorrectRevisionProvided)
    }

    "prevent deprecation with incorrect rev" in new Context {
      projects.create(id, value.asJson).success.value shouldEqual RefVersioned(id, 1L)
      projects.deprecate(id, 2L).failure.exception shouldEqual CommandRejected(IncorrectRevisionProvided)
    }

    "prevent deprecation to non existing project incorrect rev" in new Context {
      projects.create(id, value.asJson).success.value shouldEqual RefVersioned(id, 1L)
      projects.deprecate(genReference(), 1L).failure.exception shouldEqual CommandRejected(ResourceDoesNotExists)
    }

    "project cannot be used from a child resource when does not exist" in new Context {
      projects.validateUnlocked(genReference()).failure.exception shouldEqual CommandRejected(
        ParentResourceDoesNotExists)
    }

    "project cannot be used from a child resource when it is already deprecated" in new Context {
      projects.create(id, value.asJson).success.value shouldEqual RefVersioned(id, 1L)
      projects.deprecate(id, 1L).success.value shouldEqual RefVersioned(id, 2L)
      projects.validateUnlocked(id).failure.exception shouldEqual CommandRejected(ResourceIsDeprecated)
    }

    "project can be used from a child resource" in new Context {
      projects.create(id, value.asJson).success.value shouldEqual RefVersioned(id, 1L)
      projects.validateUnlocked(id).success.value shouldEqual (())
    }
  }
}

object ProjectsSpec {
  private val importResolver: ImportResolver[Try]                    = (schema: ShaclSchema) => Try(Set.empty)
  private[projects] implicit val shaclValidator: ShaclValidator[Try] = ShaclValidator(importResolver)
}

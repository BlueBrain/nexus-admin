package ch.epfl.bluebrain.nexus.admin.projects

import java.time.{Clock, Instant, ZoneId}
import java.util.UUID

import cats.effect.{ContextShift, IO}
import ch.epfl.bluebrain.nexus.admin.config.AppConfig.HttpConfig
import ch.epfl.bluebrain.nexus.admin.config.Vocabulary.nxv
import ch.epfl.bluebrain.nexus.admin.index.Index
import ch.epfl.bluebrain.nexus.admin.organizations.Organization
import ch.epfl.bluebrain.nexus.admin.projects.ProjectRejection._
import ch.epfl.bluebrain.nexus.commons.types.identity.Identity.UserRef
import ch.epfl.bluebrain.nexus.rdf.syntax.node.unsafe._
import ch.epfl.bluebrain.nexus.sourcing.Aggregate
import org.mockito.integrations.scalatest.IdiomaticMockitoFixture
import org.scalatest._

import scala.concurrent.ExecutionContext

class ProjectsSpec extends WordSpecLike with IdiomaticMockitoFixture with Matchers with EitherValues {

  private implicit val ctx: ContextShift[IO]  = IO.contextShift(ExecutionContext.global)
  private implicit val httpConfig: HttpConfig = HttpConfig("nexus", 80, "/v1", "http://nexus.example.com")

  private val instant               = Instant.now
  private implicit val clock: Clock = Clock.fixed(instant, ZoneId.systemDefault)

  private val index = mock[Index]

  private val aggF: IO[Aggregate[IO, String, ProjectEvent, ProjectState, ProjectCommand, ProjectRejection]] =
    Aggregate.inMemoryF("projects-in-memory", ProjectState.Initial, ProjectState.next, ProjectState.Eval.apply[IO])

  private val projects = aggF.map(agg => new Projects[IO](agg, index)).unsafeRunSync()

  trait Context {
    val types        = Set(nxv.Project.value)
    val caller       = UserRef("realm", "alice")
    val label        = ProjectLabel("org", "proj")
    val desc         = Some("Project description")
    val organization = Organization(UUID.randomUUID, "org", "Org description")
    val project      = Project(UUID.randomUUID, organization.id, label, desc, 1L, deprecated = false)
    val iri          = url"http://nexus.example.com/v1/org/proj".value
  }

  "The Projects operations bundle" should {

    "not create a project if it already exists" in new Context {
      index.getOrganization("org") shouldReturn Some(organization)
      index.getProject(label) shouldReturn Some(project)
      projects.create(label, desc)(caller).unsafeRunSync().left.value shouldEqual ProjectAlreadyExists
    }

    "not update a project if it doesn't exists" in new Context {
      index.getOrganization("org") shouldReturn Some(organization)
      index.getProject(label) shouldReturn None
      projects.update(label, desc)(caller).unsafeRunSync().left.value shouldEqual ProjectDoesNotExists
    }

    "not deprecate a project if it doesn't exists" in new Context {
      index.getOrganization("org") shouldReturn Some(organization)
      index.getProject(label) shouldReturn None
      projects.deprecate(label, 1L)(caller).unsafeRunSync().left.value shouldEqual ProjectDoesNotExists
    }

    "not update a project if it's deprecated" in new Context {
      index.getOrganization("org") shouldReturn Some(organization)
      index.getProject(label) shouldReturn Some(project.copy(deprecated = true))
      projects.update(label, desc)(caller).unsafeRunSync().left.value shouldEqual ProjectIsDeprecated
    }

    "not deprecate a project if it's already deprecated" in new Context {
      index.getOrganization("org") shouldReturn Some(organization)
      index.getProject(label) shouldReturn Some(project.copy(deprecated = true))
      projects.deprecate(label, 1L)(caller).unsafeRunSync().left.value shouldEqual ProjectIsDeprecated
    }

    "create a project" in new Context {
      index.getOrganization("org") shouldReturn Some(organization)
      index.getProject(label) shouldReturn None
      val created = projects.create(label, desc)(caller).unsafeRunSync().right.value
      created.id shouldEqual iri
      created.rev shouldEqual 1L
      created.deprecated shouldEqual false
      created.types shouldEqual types
      created.createdAt shouldEqual instant
      created.updatedAt shouldEqual instant
      created.createdBy shouldEqual caller
      created.updatedBy shouldEqual caller
    }

    "update a project" in new Context {
      index.getOrganization("org") shouldReturn Some(organization)
      index.getProject(label) shouldReturn None
      val created = projects.create(label, desc)(caller).unsafeRunSync().right.value
      index.getProject(label) shouldReturn Some(project.copy(id = created.uuid))
      val updated = projects.update(label, Some("New description"))(caller).unsafeRunSync().right.value
      updated.id shouldEqual iri
      updated.rev shouldEqual 2L
      updated.deprecated shouldEqual false
      updated.types shouldEqual types
      updated.createdAt shouldEqual instant
      updated.updatedAt shouldEqual instant
      updated.createdBy shouldEqual caller
      updated.updatedBy shouldEqual caller

      projects.update(label, None)(caller).unsafeRunSync().left.value shouldEqual IncorrectRevisionProvided
    }

    "deprecate a project" in new Context {
      index.getOrganization("org") shouldReturn Some(organization)
      index.getProject(label) shouldReturn None
      val created = projects.create(label, desc)(caller).unsafeRunSync().right.value
      index.getProject(label) shouldReturn Some(project.copy(id = created.uuid))
      val deprecated = projects.deprecate(label, 1L)(caller).unsafeRunSync().right.value
      deprecated.id shouldEqual iri
      deprecated.rev shouldEqual 2L
      deprecated.deprecated shouldEqual true
      deprecated.types shouldEqual types
      deprecated.createdAt shouldEqual instant
      deprecated.updatedAt shouldEqual instant
      deprecated.createdBy shouldEqual caller
      deprecated.updatedBy shouldEqual caller

      projects.deprecate(label, 42L)(caller).unsafeRunSync().left.value shouldEqual IncorrectRevisionProvided
    }

    "fetch a project" in new Context {
      index.getOrganization("org") shouldReturn Some(organization)
      index.getProject(label) shouldReturn None
      val created = projects.create(label, desc)(caller).unsafeRunSync().right.value
      val fetched = projects.fetch(created.uuid).unsafeRunSync().right.value
      fetched.id shouldEqual iri
      fetched.rev shouldEqual 1L
      fetched.deprecated shouldEqual false
      fetched.types shouldEqual types
      fetched.createdAt shouldEqual instant
      fetched.updatedAt shouldEqual instant
      fetched.createdBy shouldEqual caller
      fetched.updatedBy shouldEqual caller
      fetched.value shouldEqual SimpleProject(label.value, desc)

      projects.fetch(UUID.randomUUID).unsafeRunSync().left.value shouldEqual ProjectDoesNotExists
    }

    "fetch a project at a given revision" in new Context {
      index.getOrganization("org") shouldReturn Some(organization)
      index.getProject(label) shouldReturn None
      val created = projects.create(label, desc)(caller).unsafeRunSync().right.value
      index.getProject(label) shouldReturn Some(project.copy(id = created.uuid))
      projects.update(label, Some("New description"))(caller).unsafeRunSync().right.value
      index.getProject(label) shouldReturn Some(project.copy(id = created.uuid, rev = 2L))
      projects.update(label, Some("Another description"))(caller).unsafeRunSync().right.value
      val fetched = projects.fetch(created.uuid, 2L).unsafeRunSync().right.value
      fetched.id shouldEqual iri
      fetched.rev shouldEqual 2L
      fetched.deprecated shouldEqual false
      fetched.types shouldEqual types
      fetched.createdAt shouldEqual instant
      fetched.updatedAt shouldEqual instant
      fetched.createdBy shouldEqual caller
      fetched.updatedBy shouldEqual caller
      fetched.value shouldEqual SimpleProject(label.value, Some("New description"))

      projects.fetch(created.uuid, 4L).unsafeRunSync().left.value shouldEqual IncorrectRevisionProvided
      projects.fetch(UUID.randomUUID, 4L).unsafeRunSync().left.value shouldEqual ProjectDoesNotExists
    }
  }
}

package ch.epfl.bluebrain.nexus.admin.projects

import java.time.{Clock, Instant, ZoneId}
import java.util.UUID

import cats.effect.{ContextShift, IO}
import ch.epfl.bluebrain.nexus.admin.config.AppConfig.HttpConfig
import ch.epfl.bluebrain.nexus.admin.config.Vocabulary.nxv
import ch.epfl.bluebrain.nexus.admin.index.Index
import ch.epfl.bluebrain.nexus.admin.organizations.Organization
import ch.epfl.bluebrain.nexus.admin.projects.ProjectRejection._
import ch.epfl.bluebrain.nexus.admin.types.ResourceF
import ch.epfl.bluebrain.nexus.commons.test.io.{IOEitherValues, IOOptionValues}
import ch.epfl.bluebrain.nexus.commons.types.identity.Identity.UserRef
import ch.epfl.bluebrain.nexus.rdf.syntax.node.unsafe._
import ch.epfl.bluebrain.nexus.sourcing.Aggregate
import org.mockito.integrations.scalatest.IdiomaticMockitoFixture
import org.scalatest._

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

class ProjectsSpec
    extends WordSpecLike
    with IdiomaticMockitoFixture
    with Matchers
    with IOEitherValues
    with IOOptionValues {

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(3.seconds, 100.milliseconds)
  private implicit val ctx: ContextShift[IO]           = IO.contextShift(ExecutionContext.global)
  private implicit val httpConfig: HttpConfig          = HttpConfig("nexus", 80, "/v1", "http://nexus.example.com")

  private val instant               = Instant.now
  private implicit val clock: Clock = Clock.fixed(instant, ZoneId.systemDefault)

  private val index = mock[Index]

  private val aggF: IO[Aggregate[IO, String, ProjectEvent, ProjectState, ProjectCommand, ProjectRejection]] =
    Aggregate.inMemoryF("projects-in-memory", ProjectState.Initial, ProjectState.next, ProjectState.Eval.apply[IO])

  private val projects = aggF.map(agg => new Projects[IO](agg, index)).unsafeRunSync()

  trait Context {
    val types  = Set(nxv.Project.value)
    val caller = UserRef("realm", "alice")
    val desc   = Some("Project description")
    val orgId  = UUID.randomUUID
    val projId = UUID.randomUUID
    val iri    = url"http://nexus.example.com/v1/projects/org/proj".value
    val organization = ResourceF(
      url"http://nexus.example.com/v1/orgs/org".value,
      orgId,
      1L,
      false,
      Set(nxv.Organization.value),
      instant,
      caller,
      instant,
      caller,
      Organization("org", "Org description")
    )
    val proj    = Project("proj", "org", desc)
    val project = ResourceF(iri, projId, 1L, false, types, instant, caller, instant, caller, proj)
  }

  "The Projects operations bundle" should {

    "not create a project if it already exists" in new Context {
      index.getOrganization("org") shouldReturn Some(organization)
      index.getProject("org", "proj") shouldReturn Some(project)
      projects.create(proj)(caller).rejected[ProjectRejection] shouldEqual ProjectAlreadyExists
    }

    "not update a project if it doesn't exists" in new Context {
      index.getOrganization("org") shouldReturn Some(organization)
      index.getProject("org", "proj") shouldReturn None
      projects.update(proj)(caller).rejected[ProjectRejection] shouldEqual ProjectDoesNotExists
    }

    "not deprecate a project if it doesn't exists" in new Context {
      index.getOrganization("org") shouldReturn Some(organization)
      index.getProject("org", "proj") shouldReturn None
      projects.deprecate(proj, 1L)(caller).rejected[ProjectRejection] shouldEqual ProjectDoesNotExists
    }

    "not update a project if it's deprecated" in new Context {
      index.getOrganization("org") shouldReturn Some(organization)
      index.getOrganization(orgId) shouldReturn Some(organization)
      index.getProject("org", "proj") shouldReturn None
      val created = projects.create(proj)(caller).accepted
      index.getProject("org", "proj") shouldReturn Some(project.copy(uuid = created.uuid))
      val deprecated = projects.deprecate(proj, 1L)(caller).accepted
      index.getProject("org", "proj") shouldReturn Some(
        project.copy(uuid = deprecated.uuid, rev = 2L, deprecated = true))
      projects.update(proj)(caller).rejected[ProjectRejection] shouldEqual ProjectIsDeprecated
    }

    "not deprecate a project if it's already deprecated" in new Context {
      index.getOrganization("org") shouldReturn Some(organization)
      index.getOrganization(orgId) shouldReturn Some(organization)
      index.getProject("org", "proj") shouldReturn None
      val created = projects.create(proj)(caller).accepted
      index.getProject("org", "proj") shouldReturn Some(project.copy(uuid = created.uuid))
      val deprecated = projects.deprecate(proj, 1L)(caller).accepted
      index.getProject("org", "proj") shouldReturn Some(
        project.copy(uuid = deprecated.uuid, rev = 2L, deprecated = true))
      projects.deprecate(proj, 2L)(caller).rejected[ProjectRejection] shouldEqual ProjectIsDeprecated
    }

    "create a project" in new Context {
      index.getOrganization("org") shouldReturn Some(organization)
      index.getOrganization(orgId) shouldReturn Some(organization)
      index.getProject("org", "proj") shouldReturn None
      val created = projects.create(proj)(caller).accepted
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
      index.getOrganization(orgId) shouldReturn Some(organization)
      index.getProject("org", "proj") shouldReturn None
      val created = projects.create(proj)(caller).accepted
      index.getProject("org", "proj") shouldReturn Some(project.copy(uuid = created.uuid))
      val updated =
        projects.update(proj.copy(description = Some("New description")))(caller).accepted
      updated.id shouldEqual iri
      updated.rev shouldEqual 2L
      updated.deprecated shouldEqual false
      updated.types shouldEqual types
      updated.createdAt shouldEqual instant
      updated.updatedAt shouldEqual instant
      updated.createdBy shouldEqual caller
      updated.updatedBy shouldEqual caller

      index.getProject("org", "proj") shouldReturn Some(project.copy(uuid = created.uuid, rev = 2L))
      val updated2 = projects.update(proj.copy(description = None))(caller).accepted
      updated2.rev shouldEqual 3L
    }

    "deprecate a project" in new Context {
      index.getOrganization("org") shouldReturn Some(organization)
      index.getOrganization(orgId) shouldReturn Some(organization)
      index.getProject("org", "proj") shouldReturn None
      val created = projects.create(proj)(caller).accepted
      index.getProject("org", "proj") shouldReturn Some(project.copy(uuid = created.uuid))
      val deprecated = projects.deprecate(proj, 1L)(caller).accepted
      deprecated.id shouldEqual iri
      deprecated.rev shouldEqual 2L
      deprecated.deprecated shouldEqual true
      deprecated.types shouldEqual types
      deprecated.createdAt shouldEqual instant
      deprecated.updatedAt shouldEqual instant
      deprecated.createdBy shouldEqual caller
      deprecated.updatedBy shouldEqual caller

      projects.deprecate(proj, 42L)(caller).rejected[ProjectRejection] shouldEqual IncorrectRev(42L)
    }

    "fetch a project" in new Context {
      index.getOrganization("org") shouldReturn Some(organization)
      index.getOrganization(orgId) shouldReturn Some(organization)
      index.getProject("org", "proj") shouldReturn None
      val created = projects.create(proj)(caller).accepted
      val fetched = projects.fetch(created.uuid).some
      fetched.id shouldEqual iri
      fetched.rev shouldEqual 1L
      fetched.deprecated shouldEqual false
      fetched.types shouldEqual types
      fetched.createdAt shouldEqual instant
      fetched.updatedAt shouldEqual instant
      fetched.createdBy shouldEqual caller
      fetched.updatedBy shouldEqual caller
      fetched.value shouldEqual proj

      projects.fetch(UUID.randomUUID).ioValue shouldEqual None
    }

    "fetch a project at a given revision" in new Context {
      index.getOrganization("org") shouldReturn Some(organization)
      index.getOrganization(orgId) shouldReturn Some(organization)
      index.getProject("org", "proj") shouldReturn None
      val created = projects.create(proj)(caller).accepted
      index.getProject("org", "proj") shouldReturn Some(project.copy(uuid = created.uuid))
      projects.update(proj.copy(description = Some("New description")))(caller).accepted
      index.getProject("org", "proj") shouldReturn Some(project.copy(uuid = created.uuid, rev = 2L))
      projects.update(proj.copy(description = Some("Another description")))(caller).accepted
      val fetched = projects.fetch(created.uuid, 2L).accepted
      fetched.id shouldEqual iri
      fetched.rev shouldEqual 2L
      fetched.deprecated shouldEqual false
      fetched.types shouldEqual types
      fetched.createdAt shouldEqual instant
      fetched.updatedAt shouldEqual instant
      fetched.createdBy shouldEqual caller
      fetched.updatedBy shouldEqual caller
      fetched.value.description shouldEqual Some("New description")

      projects.fetch(created.uuid, 4L).rejected[ProjectRejection] shouldEqual IncorrectRev(4L)
      projects.fetch(UUID.randomUUID, 4L).rejected[ProjectRejection] shouldEqual ProjectDoesNotExists
    }
  }
}

package ch.epfl.bluebrain.nexus.admin.projects

import java.time.{Clock, Instant, ZoneId}
import java.util.UUID

import cats.effect.{ContextShift, IO}
import ch.epfl.bluebrain.nexus.admin.config.AppConfig.HttpConfig
import ch.epfl.bluebrain.nexus.admin.config.Vocabulary.nxv
import ch.epfl.bluebrain.nexus.admin.index.ProjectCache
import ch.epfl.bluebrain.nexus.admin.organizations.{Organization, Organizations}
import ch.epfl.bluebrain.nexus.admin.projects.ProjectRejection._
import ch.epfl.bluebrain.nexus.admin.types.ResourceF
import ch.epfl.bluebrain.nexus.commons.test.io.{IOEitherValues, IOOptionValues}
import ch.epfl.bluebrain.nexus.iam.client.types.Identity.User
import ch.epfl.bluebrain.nexus.rdf.syntax.node.unsafe._
import ch.epfl.bluebrain.nexus.sourcing.Aggregate
import org.mockito.Mockito.reset
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

  private val index = mock[ProjectCache[IO]]
  private val orgs  = mock[Organizations[IO]]

  private val aggF: IO[Agg[IO]] =
    Aggregate.inMemoryF("projects-in-memory", ProjectState.Initial, Projects.next, Projects.Eval.apply[IO])

  private val projects = aggF.map(agg => new Projects[IO](agg, index, orgs)).unsafeRunSync()

  //noinspection TypeAnnotation
  trait Context {
    val types  = Set(nxv.Project.value)
    val caller = User("realm", "alice")
    val desc   = Some("Project description")
    val orgId  = UUID.randomUUID
    val projId = UUID.randomUUID
    val iri    = url"http://nexus.example.com/v1/projects/org/proj".value
    val mappings = Map("nxv" -> url"https://bluebrain.github.io/nexus/vocabulary/".value,
                       "rdf" -> url"http://www.w3.org/1999/02/22-rdf-syntax-ns#type".value)
    val base = url"https://nexus.example.com/base".value
    val voc  = url"https://nexus.example.com/voc".value
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
    val proj    = Project("proj", "org", desc, mappings, base, Some(voc))
    val project = ResourceF(iri, projId, 1L, false, types, instant, caller, instant, caller, proj)
  }

  "The Projects operations bundle" should {

    "not create a project if it already exists" in new Context {
      orgs.fetch("org") shouldReturn IO.pure(Some(organization))
      index.getBy("org", "proj") shouldReturn IO.pure(Some(project))
      projects.create(proj)(caller).rejected[ProjectRejection] shouldEqual ProjectExists
    }

    "not create a project if its organization is deprecated" in new Context {
      orgs.fetch("org") shouldReturn IO.pure(Some(organization.copy(deprecated = true)))
      projects.create(proj)(caller).rejected[ProjectRejection] shouldEqual OrganizationIsDeprecated
    }

    "not update a project if it doesn't exists" in new Context {
      orgs.fetch("org") shouldReturn IO.pure(Some(organization))
      index.getBy("org", "proj") shouldReturn IO.pure(None)
      projects.update(proj, 1L)(caller).rejected[ProjectRejection] shouldEqual ProjectNotFound
    }

    "not deprecate a project if it doesn't exists" in new Context {
      orgs.fetch("org") shouldReturn IO.pure(Some(organization))
      index.getBy("org", "proj") shouldReturn IO.pure(None)
      projects.deprecate("org", "proj", 1L)(caller).rejected[ProjectRejection] shouldEqual ProjectNotFound
    }

    "not update a project if it's deprecated" in new Context {

      orgs.fetch("org") shouldReturn IO.pure(Some(organization))
      orgs.fetch(orgId) shouldReturn IO.pure(Some(organization))
      index.getBy("org", "proj") shouldReturn IO.pure(None)
      index.replace(any[UUID], any[ResourceF[Project]]) shouldReturn IO.pure(())
      val created = projects.create(proj)(caller).accepted
      index.getBy("org", "proj") shouldReturn IO.pure(Some(project.copy(uuid = created.uuid)))
      val deprecated = projects.deprecate("org", "proj", 1L)(caller).accepted
      index.getBy("org", "proj") shouldReturn IO.pure(
        Some(project.copy(uuid = deprecated.uuid, rev = 2L, deprecated = true)))
      projects.update(proj, 2L)(caller).rejected[ProjectRejection] shouldEqual ProjectIsDeprecated
    }

    "not deprecate a project if it's already deprecated" in new Context {
      orgs.fetch("org") shouldReturn IO.pure(Some(organization))
      orgs.fetch(orgId) shouldReturn IO.pure(Some(organization))
      index.getBy("org", "proj") shouldReturn IO.pure(None)
      val created = projects.create(proj)(caller).accepted
      index.getBy("org", "proj") shouldReturn IO.pure(Some(project.copy(uuid = created.uuid)))
      val deprecated = projects.deprecate("org", "proj", 1L)(caller).accepted
      index.getBy("org", "proj") shouldReturn IO.pure(
        Some(project.copy(uuid = deprecated.uuid, rev = 2L, deprecated = true)))
      projects.deprecate("org", "proj", 2L)(caller).rejected[ProjectRejection] shouldEqual ProjectIsDeprecated
    }

    "create a project" in new Context {
      orgs.fetch("org") shouldReturn IO.pure(Some(organization))
      orgs.fetch(orgId) shouldReturn IO.pure(Some(organization))
      index.getBy("org", "proj") shouldReturn IO.pure(None)
      val created = projects.create(proj)(caller).accepted

      created.id shouldEqual iri
      created.rev shouldEqual 1L
      created.deprecated shouldEqual false
      created.types shouldEqual types
      created.createdAt shouldEqual instant
      created.updatedAt shouldEqual instant
      created.createdBy shouldEqual caller
      created.updatedBy shouldEqual caller
      index.replace(created.uuid, eqTo(created.map(_ => proj))) was called
    }

    "update a project" in new Context {
      reset(index)
      orgs.fetch("org") shouldReturn IO.pure(Some(organization))
      orgs.fetch(orgId) shouldReturn IO.pure(Some(organization))
      index.getBy("org", "proj") shouldReturn IO.pure(None)
      index.replace(any[UUID], any[ResourceF[Project]]) shouldReturn IO.pure(())

      val created = projects.create(proj)(caller).accepted

      index.getBy("org", "proj") shouldReturn IO.pure(Some(project.copy(uuid = created.uuid)))

      val updatedProject = proj.copy(description = Some("New description"))
      val updated        = projects.update(updatedProject, 1L)(caller).accepted
      updated.id shouldEqual iri
      updated.rev shouldEqual 2L
      updated.deprecated shouldEqual false
      updated.types shouldEqual types
      updated.createdAt shouldEqual instant
      updated.updatedAt shouldEqual instant
      updated.createdBy shouldEqual caller
      updated.updatedBy shouldEqual caller

      index.getBy("org", "proj") shouldReturn IO.pure(
        Some(project.copy(uuid = created.uuid, rev = 2L, value = updatedProject)))

      val updatedProject2 = proj.copy(description = None)

      val updated2 = projects.update(updatedProject2, 2L)(caller).accepted

      updated2.rev shouldEqual 3L
      index.replace(created.uuid, created.map(_ => proj)) wasCalled once
      index.replace(created.uuid, updated.map(_ => updatedProject)) wasCalled once
      index.replace(created.uuid, updated2.map(_ => updatedProject2)) wasCalled once
    }

    "deprecate a project" in new Context {
      orgs.fetch("org") shouldReturn IO.pure(Some(organization))
      orgs.fetch(orgId) shouldReturn IO.pure(Some(organization))
      index.getBy("org", "proj") shouldReturn IO.pure(None)
      val created = projects.create(proj)(caller).accepted
      index.getBy("org", "proj") shouldReturn IO.pure(Some(project.copy(uuid = created.uuid)))
      val deprecated = projects.deprecate("org", "proj", 1L)(caller).accepted
      deprecated.id shouldEqual iri
      deprecated.rev shouldEqual 2L
      deprecated.deprecated shouldEqual true
      deprecated.types shouldEqual types
      deprecated.createdAt shouldEqual instant
      deprecated.updatedAt shouldEqual instant
      deprecated.createdBy shouldEqual caller
      deprecated.updatedBy shouldEqual caller

      projects.deprecate("org", "proj", 42L)(caller).rejected[ProjectRejection] shouldEqual IncorrectRev(2L, 42L)
    }

    "fetch a project" in new Context {
      orgs.fetch("org") shouldReturn IO.pure(Some(organization))
      orgs.fetch(orgId) shouldReturn IO.pure(Some(organization))
      index.getBy("org", "proj") shouldReturn IO.pure(None)
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

      index.getBy("org", "proj") shouldReturn IO.pure(Some(project.copy(uuid = fetched.uuid)))
      projects.fetch("org", "proj").some shouldEqual fetched

      projects.fetch(UUID.randomUUID).ioValue shouldEqual None
    }

    "fetch a project at a given revision" in new Context {
      orgs.fetch("org") shouldReturn IO.pure(Some(organization))
      orgs.fetch(orgId) shouldReturn IO.pure(Some(organization))
      index.getBy("org", "proj") shouldReturn IO.pure(None)
      val created = projects.create(proj)(caller).accepted
      index.getBy("org", "proj") shouldReturn IO.pure(Some(project.copy(uuid = created.uuid)))
      projects.update(proj.copy(description = Some("New description")), 1L)(caller).accepted
      index.getBy("org", "proj") shouldReturn IO.pure(Some(project.copy(uuid = created.uuid, rev = 2L)))
      projects.update(proj.copy(description = Some("Another description")), 2L)(caller).accepted
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

      index.getBy("org", "proj") shouldReturn IO.pure(Some(project.copy(uuid = fetched.uuid)))
      projects.fetch("org", "proj", 1L).accepted shouldEqual fetched.copy(rev = 1L, value = proj)

      projects.fetch(created.uuid, 4L).rejected[ProjectRejection] shouldEqual IncorrectRev(3L, 4L)
      projects.fetch(UUID.randomUUID, 4L).rejected[ProjectRejection] shouldEqual ProjectNotFound
    }
  }
}

package ch.epfl.bluebrain.nexus.admin.index

import java.time.Instant
import java.util.UUID

import cats.effect.IO
import ch.epfl.bluebrain.nexus.admin.config.Vocabulary.nxv
import ch.epfl.bluebrain.nexus.admin.organizations.{Organization, Organizations}
import ch.epfl.bluebrain.nexus.admin.projects.ProjectEvent.{ProjectCreated, ProjectUpdated}
import ch.epfl.bluebrain.nexus.admin.projects.{Project, Projects}
import ch.epfl.bluebrain.nexus.admin.types.ResourceF
import ch.epfl.bluebrain.nexus.commons.test.io.{IOEitherValues, IOOptionValues}
import ch.epfl.bluebrain.nexus.commons.types.identity.Identity.UserRef
import ch.epfl.bluebrain.nexus.rdf.syntax.node.unsafe._
import org.mockito.Mockito.verify
import org.mockito.integrations.scalatest.IdiomaticMockitoFixture
import org.scalatest.{Matchers, WordSpecLike}

class ProjectsIndexerSpec
    extends WordSpecLike
    with IdiomaticMockitoFixture
    with Matchers
    with IOEitherValues
    with IOOptionValues {

  trait Context {
    val instant = Instant.now
    val types   = Set(nxv.Project.value)
    val caller  = UserRef("realm", "alice")
    val orgId   = UUID.randomUUID
    val projId  = UUID.randomUUID
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
    val proj = Project("proj", "org", Some("Project description"))
    val project = ResourceF(url"http://nexus.example.com/v1/projects/org/proj".value,
                            projId,
                            1L,
                            false,
                            types,
                            instant,
                            caller,
                            instant,
                            caller,
                            proj)

    val orgs: Organizations[IO]              = mock[Organizations[IO]]
    val projects: Projects[IO]               = mock[Projects[IO]]
    val index: Index[IO]                     = mock[Index[IO]]
    val projectsIndexer: ProjectsIndexer[IO] = new ProjectsIndexer[IO](projects, orgs, index)
  }

  "Projects indexer" should {

    "index organization and project when project is created" in new Context {

      orgs.fetch(organization.uuid) shouldReturn IO.pure(Some(organization))
      projects.fetch(project.uuid) shouldReturn IO.pure(Some(project))
      index.updateOrganization(organization) shouldReturn IO.pure(true)
      index.updateProject(project) shouldReturn IO.pure(true)

      projectsIndexer
        .index(
          List(
            ProjectCreated(project.uuid, organization.uuid, proj.label, proj.description, 1L, instant, caller),
          ))
        .unsafeRunSync()

      verify(index).updateOrganization(organization)
      verify(index).updateProject(project)
    }

    "index project only for other project events" in new Context {

      index.updateProject(project) shouldReturn IO.pure(true)
      projects.fetch(project.uuid) shouldReturn IO.pure(Some(project))

      projectsIndexer
        .index(
          List(
            ProjectUpdated(project.uuid, proj.label, proj.description, 1L, instant, caller),
          ))
        .unsafeRunSync()

      verify(index).updateProject(project)
    }
  }

}

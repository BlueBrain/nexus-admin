package ch.epfl.bluebrain.nexus.admin.index

import java.time.Instant
import java.util.UUID

import akka.util.Timeout
import cats.effect.IO
import ch.epfl.bluebrain.nexus.admin.config.Vocabulary.nxv
import ch.epfl.bluebrain.nexus.admin.organizations.Organization
import ch.epfl.bluebrain.nexus.admin.projects.Project
import ch.epfl.bluebrain.nexus.admin.types.ResourceF
import ch.epfl.bluebrain.nexus.commons.test.Randomness
import ch.epfl.bluebrain.nexus.commons.types.search.Pagination
import ch.epfl.bluebrain.nexus.iam.client.types.Caller
import ch.epfl.bluebrain.nexus.rdf.syntax.node.unsafe._
import ch.epfl.bluebrain.nexus.service.test.ActorSystemFixture
import org.scalatest.{Inspectors, Matchers, OptionValues}

import scala.concurrent.duration._

class DistributedDataIndexSpec
    extends ActorSystemFixture("DistributedDataIndexSpec", true)
    with Randomness
    with Matchers
    with OptionValues
    with Inspectors {

  val consistencyTimeout       = 5 seconds
  val askTimeout               = Timeout(consistencyTimeout)
  private val instant          = Instant.now()
  private implicit val subject = Caller.anonymous.subject

  val index        = DistributedDataIndex[IO](askTimeout, consistencyTimeout)
  val organization = Organization(genString(), genString())
  val orgResource = ResourceF(
    url"http://nexus.example.com/v1/orgs/${organization.label}".value,
    UUID.randomUUID(),
    2L,
    false,
    Set(nxv.Organization.value),
    instant,
    subject,
    instant,
    subject,
    organization
  )
  val project = Project(genString(), organization.label, Some(genString()))
  val projectResource = ResourceF(url"http://nexus.example.com/v1/orgs/org".value,
                                  UUID.randomUUID(),
                                  1L,
                                  false,
                                  Set(nxv.Project.value),
                                  instant,
                                  subject,
                                  instant,
                                  subject,
                                  project)

  "DistributedDataIndex" should {

    "index organization by label" in {

      index.updateOrganization(orgResource).unsafeRunSync()

      index.getOrganization(organization.label).unsafeRunSync().value shouldEqual orgResource

    }

    "index project by label" in {

      index.updateOrganization(orgResource).unsafeRunSync()
      index.updateProject(projectResource).unsafeRunSync()
      index.getProject(project.organization, project.label).unsafeRunSync().value shouldEqual projectResource
    }

    "list organizations" in {
      val orgLabels = (1 to 50).map(_ => genString())

      val orgResources = orgLabels.map { label =>
        val organization = Organization(genString(), label)
        ResourceF(
          url"http://nexus.example.com/v1/orgs/${organization.label}".value,
          UUID.randomUUID(),
          2L,
          false,
          Set(nxv.Organization.value),
          instant,
          subject,
          instant,
          subject,
          organization
        )
      } :+ orgResource

      orgResources.foreach(org => index.updateOrganization(org).unsafeRunSync())
      forAll(orgResources) { org =>
        index.getOrganization(org.value.label).unsafeRunSync().value shouldEqual org
      }

      val sortedOrgs = orgResources.sortBy(org => org.value.label)

      val fetchedOrganizations = index.listOrganizations(Pagination(0, 100)).unsafeRunSync()
      fetchedOrganizations.size shouldEqual 51
      fetchedOrganizations shouldEqual sortedOrgs
      index.listOrganizations(Pagination(0, 10)).unsafeRunSync().size shouldEqual 10
      index.listOrganizations(Pagination(0, 10)).unsafeRunSync() shouldEqual sortedOrgs.slice(0, 10)
      index.listOrganizations(Pagination(10, 10)).unsafeRunSync() shouldEqual sortedOrgs.slice(10, 20)
      index.listOrganizations(Pagination(40, 20)).unsafeRunSync() shouldEqual sortedOrgs.slice(40, 52)

    }

    "list projects" in {
      val projectLabels        = (1 to 50).map(_ => genString())
      val orgLabel             = genString()
      val projectsOrganization = Organization(genString(), orgLabel)

      val projectsOrgResource = ResourceF(
        url"http://nexus.example.com/v1/orgs/${projectsOrganization.label}".value,
        UUID.randomUUID(),
        2L,
        false,
        Set(nxv.Organization.value),
        instant,
        subject,
        instant,
        subject,
        projectsOrganization
      )

      val projectResources = projectLabels.map { label =>
        val project = Project(label, projectsOrganization.label, Some(genString()))
        ResourceF(
          url"http://nexus.example.com/v1/projects/${projectsOrganization.label}/${project.label}".value,
          UUID.randomUUID(),
          1L,
          false,
          Set(nxv.Project.value),
          instant,
          subject,
          instant,
          subject,
          project
        )
      } :+ projectResource

      index.updateOrganization(projectsOrgResource).unsafeRunSync()
      projectResources.foreach(project => index.updateProject(project).unsafeRunSync())
      forAll(projectResources) { project =>
        index.getProject(project.value.organization, project.value.label).unsafeRunSync().value shouldEqual project
      }

      val sortedProjects = projectResources.sortBy(project => s"${project.value.organization}/${project.value.label}")

      val fetchedProjects = index.listProjects(Pagination(0, 100)).unsafeRunSync()
      fetchedProjects.size shouldEqual 51
      fetchedProjects shouldEqual sortedProjects
      index.listProjects(Pagination(0, 10)).unsafeRunSync().size shouldEqual 10
      index.listProjects(Pagination(0, 10)).unsafeRunSync() shouldEqual sortedProjects.slice(0, 10)
      index.listProjects(Pagination(10, 10)).unsafeRunSync() shouldEqual sortedProjects.slice(10, 20)
      index.listProjects(Pagination(40, 20)).unsafeRunSync() shouldEqual sortedProjects.slice(40, 52)

    }

    "list projects for organization" in {
      val orgLabels = (1 to 3).map(_ => genString())

      val orgResources = orgLabels.map { label =>
        val organization = Organization(genString(), label)
        ResourceF(
          url"http://nexus.example.com/v1/orgs/${organization.label}".value,
          UUID.randomUUID(),
          2L,
          false,
          Set(nxv.Organization.value),
          instant,
          subject,
          instant,
          subject,
          organization
        )

      }
      val projectResources = orgResources.map { org =>
        org.value.label -> (1 to 20).map { _ =>
          val project = Project(genString(), org.value.label, Some(genString()))
          ResourceF(
            url"http://nexus.example.com/v1/projects/${project.organization}/${project.label}".value,
            UUID.randomUUID(),
            1L,
            false,
            Set(nxv.Project.value),
            instant,
            subject,
            instant,
            subject,
            project
          )
        }
      }.toMap

      orgResources.foreach(index.updateOrganization(_).unsafeRunSync())
      projectResources.values.flatten.foreach(index.updateProject(_).unsafeRunSync())

      forAll(projectResources.keys) { orgLabel =>
        val orderedProjects =
          projectResources(orgLabel).sortBy(project => s"${project.value.organization}/${project.value.label}")
        index.listProjects(orgLabel, Pagination(0, 20)).unsafeRunSync() shouldEqual orderedProjects
        index.listProjects(orgLabel, Pagination(5, 5)).unsafeRunSync() shouldEqual orderedProjects.slice(5, 10)

      }
    }

    "index updated organization" in {
      val updated = orgResource.copy(rev = orgResource.rev + 1)
      index.updateOrganization(updated).unsafeRunSync()
      index.getOrganization(updated.value.label).unsafeRunSync().value shouldEqual updated
    }

    "ignore organization updates  with lower  revision" in {
      val updated = orgResource.copy(rev = orgResource.rev + 1)
      index.updateOrganization(updated).unsafeRunSync()
      index.updateOrganization(orgResource).unsafeRunSync()
      index.getOrganization(updated.value.label).unsafeRunSync().value shouldEqual updated
    }

    "index updated project" in {
      val updated = projectResource.copy(rev = orgResource.rev + 1)
      index.updateProject(updated).unsafeRunSync()
      index.getProject(updated.value.organization, updated.value.label).unsafeRunSync().value shouldEqual updated
    }

    "ignore project updates  with lower  revision" in {
      val updated = projectResource.copy(rev = orgResource.rev + 1)
      index.updateProject(updated).unsafeRunSync()
      index.updateProject(projectResource).unsafeRunSync()
      index.getProject(updated.value.organization, updated.value.label).unsafeRunSync().value shouldEqual updated
    }

  }
}

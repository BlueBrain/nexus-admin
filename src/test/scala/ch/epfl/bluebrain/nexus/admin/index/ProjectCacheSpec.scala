package ch.epfl.bluebrain.nexus.admin.index

import java.time.Instant
import java.util.UUID

import cats.effect.{IO, Timer}
import ch.epfl.bluebrain.nexus.admin.config.Settings
import ch.epfl.bluebrain.nexus.admin.config.Vocabulary.nxv
import ch.epfl.bluebrain.nexus.admin.organizations.Organization
import ch.epfl.bluebrain.nexus.admin.projects.Project
import ch.epfl.bluebrain.nexus.admin.types.ResourceF
import ch.epfl.bluebrain.nexus.commons.test.Randomness
import ch.epfl.bluebrain.nexus.commons.test.io.IOOptionValues
import ch.epfl.bluebrain.nexus.commons.types.search.Pagination
import ch.epfl.bluebrain.nexus.commons.types.search.QueryResult.UnscoredQueryResult
import ch.epfl.bluebrain.nexus.commons.types.search.QueryResults.UnscoredQueryResults
import ch.epfl.bluebrain.nexus.iam.client.types.Caller
import ch.epfl.bluebrain.nexus.rdf.syntax.node.unsafe._
import ch.epfl.bluebrain.nexus.service.test.ActorSystemFixture
import org.scalatest.{Inspectors, Matchers, OptionValues}

class ProjectCacheSpec
    extends ActorSystemFixture("ProjectCacheSpec", true)
    with Randomness
    with Matchers
    with OptionValues
    with Inspectors
    with IOOptionValues {

  private val instant                   = Instant.now()
  private implicit val timer: Timer[IO] = IO.timer(system.dispatcher)
  private implicit val subject          = Caller.anonymous.subject
  private implicit val appConfig        = Settings(system).appConfig
  private implicit val keyStoreConfig   = appConfig.keyValueStore

  val orgIndex     = OrganizationCache[IO]
  val index        = ProjectCache[IO]
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
  val mappings = Map("nxv" -> url"https://bluebrain.github.io/nexus/vocabulary/".value,
                     "rdf" -> url"http://www.w3.org/1999/02/22-rdf-syntax-ns#type".value)
  val base = url"http://nexus.example.com/base".value
  val voc  = url"http://nexus.example.com/voc".value
  val project =
    Project(genString(), UUID.randomUUID(), organization.label, Some(genString()), mappings, base, voc)
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

    "index project" in {
      index.replace(projectResource.uuid, projectResource).ioValue shouldEqual (())
      index.get(projectResource.uuid).some shouldEqual projectResource
      index.getBy(orgResource.value.label, projectResource.value.label).some shouldEqual projectResource
    }

    "list projects" in {
      val projectLabels        = (1 to 15).map(_ => genString())
      val orgLabel             = genString()
      val projectsOrganization = Organization(orgLabel, "description")

      val projectLabels2        = (1 to 10).map(_ => genString())
      val orgLabel2             = genString()
      val projectsOrganization2 = Organization(orgLabel2, "description2")

      val projectResources = projectLabels.map { label =>
        val project =
          Project(label, UUID.randomUUID(), projectsOrganization.label, Some(genString()), mappings, base, voc)
        projectResource.copy(
          id = url"http://nexus.example.com/v1/projects/${projectsOrganization.label}/${project.label}".value,
          uuid = UUID.randomUUID(),
          value = project)
      }

      val projectResources2 = projectLabels2.map { label =>
        val project =
          Project(label, UUID.randomUUID(), projectsOrganization2.label, Some(genString()), mappings, base, voc)
        projectResource.copy(
          id = url"http://nexus.example.com/v1/projects/${projectsOrganization.label}/${project.label}".value,
          uuid = UUID.randomUUID(),
          value = project)
      }

      val combined = projectResources ++ projectResources2

      combined.foreach(project => index.replace(project.uuid, project).ioValue)
      forAll(combined) { project =>
        index.getBy(project.value.organizationLabel, project.value.label).some shouldEqual project
        index.get(project.uuid).some shouldEqual project
      }

      val sortedCombined =
        (combined :+ projectResource)
          .sortBy(project => s"${project.value.organizationLabel}/${project.value.label}")
          .toList
          .map(UnscoredQueryResult(_))

      val sortedProjects =
        projectResources
          .sortBy(project => s"${project.value.organizationLabel}/${project.value.label}")
          .toList
          .map(UnscoredQueryResult(_))

      val sortedProjects2 =
        projectResources2
          .sortBy(project => s"${project.value.organizationLabel}/${project.value.label}")
          .toList
          .map(UnscoredQueryResult(_))

      index.list(Pagination(0, 100)).ioValue shouldEqual UnscoredQueryResults(26L, sortedCombined)

      index.list(orgLabel, Pagination(0, 100)).ioValue shouldEqual UnscoredQueryResults(15L, sortedProjects)
      index.list(orgLabel2, Pagination(0, 100)).ioValue shouldEqual UnscoredQueryResults(10L, sortedProjects2)
      index.list(orgLabel2, Pagination(0, 5)).ioValue shouldEqual UnscoredQueryResults(10L, sortedProjects2.slice(0, 5))
    }
  }
}

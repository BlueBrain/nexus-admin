package ch.epfl.bluebrain.nexus.admin.index

import java.time.Instant
import java.util.UUID

import akka.testkit._
import cats.effect.{IO, Timer}
import ch.epfl.bluebrain.nexus.admin.config.Settings
import ch.epfl.bluebrain.nexus.admin.config.Vocabulary.nxv
import ch.epfl.bluebrain.nexus.admin.organizations.Organization
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

import scala.concurrent.duration._

class OrganizationCacheSpec
    extends ActorSystemFixture("OrganizationCacheSpec", true)
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

  val index        = OrganizationCache[IO]
  val organization = Organization(genString(), Some(genString()))
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

  override implicit def patienceConfig: PatienceConfig = PatienceConfig(3.seconds.dilated, 5.milliseconds)

  "DistributedDataIndex" should {

    "index organization" in {
      index.replace(orgResource.uuid, orgResource).ioValue shouldEqual (())
      index.get(orgResource.uuid).some shouldEqual orgResource
      index.getBy(organization.label).some shouldEqual orgResource
    }

    "list organizations" in {
      val orgLabels = (1 to 50).map(_ => genString())

      val orgResources = orgLabels.map { label =>
        val organization = Organization(label, Some(genString()))
        orgResource.copy(id = url"http://nexus.example.com/v1/orgs/${organization.label}".value,
                         uuid = UUID.randomUUID(),
                         value = organization)
      } :+ orgResource

      orgResources.foreach(org => index.replace(org.uuid, org).ioValue)
      forAll(orgResources) { org =>
        index.get(org.uuid).some shouldEqual org
        index.getBy(org.value.label).some shouldEqual org
      }

      val sortedOrgs = orgResources.sortBy(org => org.value.label).toList.map(UnscoredQueryResult(_))

      index.list(Pagination(0, 100)).ioValue shouldEqual UnscoredQueryResults(51L, sortedOrgs)
      index.list(Pagination(0, 10)).ioValue shouldEqual UnscoredQueryResults(51L, sortedOrgs.slice(0, 10))
      index.list(Pagination(10, 10)).ioValue shouldEqual UnscoredQueryResults(51L, sortedOrgs.slice(10, 20))
      index.list(Pagination(40, 20)).ioValue shouldEqual UnscoredQueryResults(51L, sortedOrgs.slice(40, 52))
    }

    "index updated organization" in {
      index.replace(orgResource.uuid, orgResource).ioValue shouldEqual (())

      val updated = orgResource.copy(rev = orgResource.rev + 1)
      index.replace(orgResource.uuid, updated).ioValue shouldEqual (())

      index.get(orgResource.uuid).some shouldEqual updated
      index.getBy(organization.label).some shouldEqual updated
    }
  }
}

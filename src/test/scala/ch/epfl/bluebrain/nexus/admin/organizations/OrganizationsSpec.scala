package ch.epfl.bluebrain.nexus.admin.organizations

import java.time.{Clock, Instant, ZoneId}

import cats.effect.{ContextShift, IO, Timer}
import ch.epfl.bluebrain.nexus.admin.config.AppConfig.HttpConfig
import ch.epfl.bluebrain.nexus.admin.config.Settings
import ch.epfl.bluebrain.nexus.admin.config.Vocabulary.nxv
import ch.epfl.bluebrain.nexus.admin.index.OrganizationCache
import ch.epfl.bluebrain.nexus.admin.organizations.OrganizationRejection._
import ch.epfl.bluebrain.nexus.admin.organizations.OrganizationState._
import ch.epfl.bluebrain.nexus.admin.organizations.Organizations._
import ch.epfl.bluebrain.nexus.admin.types.ResourceF
import ch.epfl.bluebrain.nexus.commons.test.Randomness
import ch.epfl.bluebrain.nexus.commons.test.io.{IOEitherValues, IOOptionValues}
import ch.epfl.bluebrain.nexus.iam.client.types.Caller
import ch.epfl.bluebrain.nexus.iam.client.types.Identity.Subject
import ch.epfl.bluebrain.nexus.rdf.syntax.node.unsafe._
import ch.epfl.bluebrain.nexus.service.test.ActorSystemFixture
import ch.epfl.bluebrain.nexus.sourcing.Aggregate
import org.scalatest.Matchers
import org.scalatest.concurrent.ScalaFutures

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

class OrganizationsSpec
    extends ActorSystemFixture("OrganizationsSpec", true)
    with ScalaFutures
    with Randomness
    with IOOptionValues
    with IOEitherValues
    with Matchers {

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(3 seconds, 100 milliseconds)

  private implicit val clock: Clock          = Clock.fixed(Instant.ofEpochSecond(3600), ZoneId.systemDefault())
  private implicit val http: HttpConfig      = HttpConfig("some", 8080, "/v1", "http://nexus.example.com")
  private implicit val ctx: ContextShift[IO] = IO.contextShift(ExecutionContext.global)
  implicit val timer: Timer[IO]              = IO.timer(system.dispatcher)

  private implicit val caller: Subject = Caller.anonymous.subject
  private val instant                  = clock.instant()
  private implicit val appConfig       = Settings(system).appConfig
  private implicit val keyStoreConfig  = appConfig.keyValueStore

  private val aggF: IO[Agg[IO]] = Aggregate.inMemory[IO, String]("organizations", Initial, next, evaluate[IO])

  private val index = OrganizationCache[IO]

  private val orgs = aggF.map(new Organizations(_, index)).unsafeRunSync()

  "Organizations operations bundle" should {

    "create and fetch organizations " in {
      val organization = Organization(genString(), genString())

      val metadata = orgs.create(organization).accepted

      metadata.id shouldEqual url"http://nexus.example.com/v1/orgs/${organization.label}".value

      metadata.rev shouldEqual 1L

      metadata.deprecated shouldEqual false
      metadata.types shouldEqual Set(nxv.Organization.value)
      metadata.createdAt shouldEqual instant
      metadata.createdBy shouldEqual caller
      metadata.updatedAt shouldEqual instant
      metadata.updatedBy shouldEqual caller

      val organizationResource = metadata.map(_ => organization)
      orgs.fetch(organization.label).some shouldEqual organizationResource

      val nonExistentLabel = genString()

      orgs.fetch(nonExistentLabel).unsafeRunSync() shouldEqual None

    }

    "update organization" in {
      val organization = Organization(genString(), genString())

      val metadata = orgs.create(organization).accepted

      val updatedOrg = organization.copy(label = genString(), description = genString())

      val resource = orgs.update(organization.label, updatedOrg, 1L).accepted

      resource shouldEqual ResourceF.unit(
        url"http://nexus.example.com/v1/orgs/${updatedOrg.label}".value,
        metadata.uuid,
        2L,
        false,
        Set(nxv.Organization.value),
        instant,
        caller,
        instant,
        caller
      )

      orgs.fetch(updatedOrg.label).some shouldEqual ResourceF(
        url"http://nexus.example.com/v1/orgs/${updatedOrg.label}".value,
        metadata.uuid,
        2L,
        false,
        Set(nxv.Organization.value),
        instant,
        caller,
        instant,
        caller,
        updatedOrg
      )

    }

    "deprecate organizations" in {
      val organization = Organization(genString(), genString())

      val metadata = orgs.create(organization).accepted

      val resource = orgs.deprecate(organization.label, 1L).accepted

      resource shouldEqual metadata.copy(rev = 2L, deprecated = true)

      orgs.fetch(organization.label).some shouldEqual resource.map(_ => organization)
    }

    "fetch organizations by revision" in {
      val organization = Organization(genString(), genString())

      val metadata = orgs.create(organization).accepted

      val updatedOrg = organization.copy(description = genString())

      orgs.update(updatedOrg.label, updatedOrg, 1L).accepted

      orgs.fetch(updatedOrg.label, Some(1L)).some shouldEqual ResourceF(
        url"http://nexus.example.com/v1/orgs/${organization.label}".value,
        metadata.uuid,
        1L,
        false,
        Set(nxv.Organization.value),
        instant,
        caller,
        instant,
        caller,
        organization
      )
    }

    "reject update when revision is incorrect" in {
      val organization = Organization(genString(), genString())

      orgs.create(organization).unsafeRunSync()

      val updatedOrg = organization.copy(description = genString())

      orgs.update(updatedOrg.label, updatedOrg, 2L).rejected[OrganizationRejection] shouldEqual IncorrectRev(1L, 2L)
    }

    "reject deprecation when revision is incorrect" in {
      val organization = Organization(genString(), genString())

      orgs.create(organization).unsafeRunSync()

      orgs.deprecate(organization.label, 2L).rejected[OrganizationRejection] shouldEqual IncorrectRev(1L, 2L)
    }

    "return None if organization doesn't exist" in {
      val label = genString()
      orgs.fetch(label).unsafeRunSync() shouldEqual None
    }
  }
}

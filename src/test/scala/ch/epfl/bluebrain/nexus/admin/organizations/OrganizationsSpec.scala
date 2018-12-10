package ch.epfl.bluebrain.nexus.admin.organizations

import java.time.{Clock, Instant, ZoneId}

import akka.util.Timeout
import cats.effect.{ContextShift, IO}
import ch.epfl.bluebrain.nexus.admin.config.AppConfig.HttpConfig
import ch.epfl.bluebrain.nexus.admin.config.Vocabulary.nxv
import ch.epfl.bluebrain.nexus.admin.index.DistributedDataIndex
import ch.epfl.bluebrain.nexus.admin.organizations.OrganizationRejection._
import ch.epfl.bluebrain.nexus.admin.organizations.OrganizationState._
import ch.epfl.bluebrain.nexus.admin.organizations.Organizations._
import ch.epfl.bluebrain.nexus.admin.types.ResourceF
import ch.epfl.bluebrain.nexus.commons.test.Randomness
import ch.epfl.bluebrain.nexus.iam.client.types.Caller
import ch.epfl.bluebrain.nexus.iam.client.types.Identity.Subject
import ch.epfl.bluebrain.nexus.rdf.syntax.node.unsafe._
import ch.epfl.bluebrain.nexus.service.test.ActorSystemFixture
import ch.epfl.bluebrain.nexus.sourcing.Aggregate
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{EitherValues, Matchers, OptionValues}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

class OrganizationsSpec
    extends ActorSystemFixture("OrganizationsSpec", true)
    with ScalaFutures
    with Randomness
    with EitherValues
    with Matchers
    with OptionValues {

  private implicit val clock: Clock          = Clock.fixed(Instant.ofEpochSecond(3600), ZoneId.systemDefault())
  private implicit val http                  = HttpConfig("some", 8080, "/v1", "http://nexus.example.com")
  private implicit val ctx: ContextShift[IO] = IO.contextShift(ExecutionContext.global)
  private implicit val caller: Subject       = Caller.anonymous.subject
  private val instant                        = clock.instant()

  val aggF: IO[Agg[IO]] = Aggregate.inMemory[IO, String]("organizations", Initial, next, evaluate[IO])

  val consistencyTimeout = 5 seconds
  val askTimeout         = Timeout(consistencyTimeout)
  val index              = DistributedDataIndex[IO](askTimeout, consistencyTimeout)

  val orgs = aggF.map(new Organizations(_, index)).unsafeRunSync()

  "Organizations operations bundle" should {

    "create and fetch organizations " in {
      val organization = Organization(genString(), genString())

      val metadata = orgs.create(organization).unsafeRunSync().right.value

      metadata.id shouldEqual url"http://nexus.example.com/v1/orgs/${organization.label}".value

      metadata.rev shouldEqual 1L

      metadata.deprecated shouldEqual false
      metadata.types shouldEqual Set(nxv.Organization.value)
      metadata.createdAt shouldEqual instant
      metadata.createdBy shouldEqual caller
      metadata.updatedAt shouldEqual instant
      metadata.updatedBy shouldEqual caller

      val organizationResource = metadata.map(_ => organization)
      orgs.fetch(organization.label).unsafeRunSync().value shouldEqual organizationResource

      val nonExistentLabel = genString()

      orgs.fetch(nonExistentLabel).unsafeRunSync() shouldEqual None

    }

    "update organization" in {
      val organization = Organization(genString(), genString())

      val metadata = orgs.create(organization).unsafeRunSync().right.value

      val updatedOrg = organization.copy(label = genString(), description = genString())

      val resource = orgs.update(organization.label, updatedOrg, 1L).unsafeRunSync().right.value

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

      orgs.fetch(updatedOrg.label).unsafeRunSync().value shouldEqual ResourceF(
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

      val metadata = orgs.create(organization).unsafeRunSync().right.value

      val resource = orgs.deprecate(organization.label, 1L).unsafeRunSync().right.value

      resource shouldEqual metadata.copy(rev = 2L, deprecated = true)

      orgs.fetch(organization.label).unsafeRunSync().value shouldEqual resource.map(_ => organization)
    }

    "fetch organizations by revision" in {
      val organization = Organization(genString(), genString())

      val metadata = orgs.create(organization).unsafeRunSync().right.value

      val updatedOrg = organization.copy(description = genString())

      orgs.update(updatedOrg.label, updatedOrg, 1L).unsafeRunSync().right.value

      orgs.fetch(updatedOrg.label, 1L).unsafeRunSync().value shouldEqual ResourceF(
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

      orgs.update(updatedOrg.label, updatedOrg, 2L).unsafeRunSync() shouldEqual Left(IncorrectRev(1L, 2L))
    }

    "reject deprecation when revision is incorrect" in {
      val organization = Organization(genString(), genString())

      orgs.create(organization).unsafeRunSync()

      orgs.deprecate(organization.label, 2L).unsafeRunSync() shouldEqual Left(IncorrectRev(1L, 2L))
    }

    "return None if organization doesn't exist" in {
      val label = genString()
      orgs.fetch(label).unsafeRunSync() shouldEqual None
    }
  }
}

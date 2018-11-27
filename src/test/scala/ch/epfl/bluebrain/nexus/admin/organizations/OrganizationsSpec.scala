package ch.epfl.bluebrain.nexus.admin.organizations

import java.time.{Clock, Instant, ZoneId}

import cats.effect.{ContextShift, IO}
import ch.epfl.bluebrain.nexus.admin.config.AppConfig.HttpConfig
import ch.epfl.bluebrain.nexus.admin.config.Vocabulary.nxv
import ch.epfl.bluebrain.nexus.admin.index.Index
import ch.epfl.bluebrain.nexus.admin.organizations.OrganizationState._
import ch.epfl.bluebrain.nexus.admin.types.ResourceF
import ch.epfl.bluebrain.nexus.commons.test.Randomness
import ch.epfl.bluebrain.nexus.commons.types.identity.Identity
import ch.epfl.bluebrain.nexus.rdf.syntax.node.unsafe._
import ch.epfl.bluebrain.nexus.sourcing.Aggregate
import org.mockito.integrations.scalatest.IdiomaticMockitoFixture
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{EitherValues, Matchers, OptionValues, WordSpecLike}

import scala.concurrent.ExecutionContext

class OrganizationsSpec
    extends WordSpecLike
    with IdiomaticMockitoFixture
    with ScalaFutures
    with Randomness
    with EitherValues
    with Matchers
    with OptionValues {

  private implicit val clock: Clock          = Clock.fixed(Instant.ofEpochSecond(3600), ZoneId.systemDefault())
  private implicit val http                  = HttpConfig("some", 8080, "/v1", "http://nexus.example.com")
  private implicit val ctx: ContextShift[IO] = IO.contextShift(ExecutionContext.global)
  private implicit val identity              = Identity.Anonymous()
  private val instant                        = clock.instant()

  val aggF
    : IO[Aggregate[IO, String, OrganizationEvent, OrganizationState, OrganizationCommand, OrganizationRejection]] =
    Aggregate.inMemory[IO, String]("organizations", Initial, next, evaluate[IO])

  val index = mock[Index]
  val orgs  = aggF.map(new Organizations(_, index)).unsafeRunSync()

  "Organizations API" should {

    "create and fetch organizations " in {
      val organization = Organization(genString(), genString())

      index.getOrganization(organization.label) shouldReturn None

      val metadata = orgs.create(organization).unsafeRunSync().right.value

      metadata.id shouldEqual url"http://nexus.example.com/v1/orgs/${organization.label}".value

      metadata.rev shouldEqual 1L

      metadata.deprecated shouldEqual false
      metadata.types shouldEqual Set(nxv.Organization)
      metadata.createdAt shouldEqual instant
      metadata.createdBy shouldEqual identity
      metadata.updatedAt shouldEqual instant
      metadata.updatedBy shouldEqual identity

      val organizationResource = metadata.map(_ => organization)
      index.getOrganization(organization.label) shouldReturn Some(organizationResource)

      orgs.fetch(organization.label).unsafeRunSync().value shouldEqual organizationResource

      val nonExistentLabel = genString()

      index.getOrganization(nonExistentLabel) shouldReturn None
      orgs.fetch(nonExistentLabel).unsafeRunSync() shouldEqual None

    }

    "update organization" in {
      val organization = Organization(genString(), genString())

      index.getOrganization(organization.label) shouldReturn None
      val metadata = orgs.create(organization).unsafeRunSync().right.value

      val updatedOrg = organization.copy(label = genString(), description = genString())

      index.getOrganization(organization.label) shouldReturn Some(metadata.map(_ => organization))
      val resource = orgs.update(organization.label, updatedOrg, 1L).unsafeRunSync().right.value

      resource shouldEqual ResourceF.unit(
        url"http://nexus.example.com/v1/orgs/${updatedOrg.label}".value,
        metadata.uuid,
        2L,
        false,
        Set(nxv.Organization),
        instant,
        identity,
        instant,
        identity
      )

      index.getOrganization(updatedOrg.label) shouldReturn Some(
        metadata
          .map(_ => updatedOrg)
          .copy(rev = 2L, id = url"http://nexus.example.com/v1/orgs/${updatedOrg.label}".value))

      orgs.fetch(updatedOrg.label).unsafeRunSync().value shouldEqual ResourceF(
        url"http://nexus.example.com/v1/orgs/${updatedOrg.label}".value,
        metadata.uuid,
        2L,
        false,
        Set(nxv.Organization),
        instant,
        identity,
        instant,
        identity,
        updatedOrg
      )

    }

    "deprecate organizations" in {
      val organization = Organization(genString(), genString())

      index.getOrganization(organization.label) shouldReturn None
      val metadata = orgs.create(organization).unsafeRunSync().right.value

      index.getOrganization(organization.label) shouldReturn Some(metadata.map(_ => organization))

      val resource = orgs.deprecate(organization.label, 1L).unsafeRunSync().right.value

      resource shouldEqual metadata.copy(rev = 2L, deprecated = true)

      index.getOrganization(organization.label) shouldReturn Some(resource.map(_ => organization))
      orgs.fetch(organization.label).unsafeRunSync().value shouldEqual resource.map(_ => organization)

    }

    "fetch organizations by revision" in {
      val organization = Organization(genString(), genString())

      index.getOrganization(organization.label) shouldReturn None
      val metadata = orgs.create(organization).unsafeRunSync().right.value

      index.getOrganization(organization.label) shouldReturn Some(metadata.map(_ => organization))
      val updatedOrg = organization.copy(description = genString())

      index.getOrganization(organization.label) shouldReturn Some(metadata.map(_ => organization))
      orgs.update(updatedOrg.label, updatedOrg, 1L).unsafeRunSync().right.value

      index.getOrganization(organization.label) shouldReturn Some(metadata.map(_ => updatedOrg))

      orgs.fetch(updatedOrg.label, 1L).unsafeRunSync().value shouldEqual ResourceF(
        url"http://nexus.example.com/v1/orgs/${organization.label}".value,
        metadata.uuid,
        1L,
        false,
        Set(nxv.Organization),
        instant,
        identity,
        instant,
        identity,
        organization
      )
    }

    "reject update when revision is incorrect" in {
      val organization = Organization(genString(), genString())

      index.getOrganization(organization.label) shouldReturn None
      val metadata = orgs.create(organization).unsafeRunSync().right.value

      val updatedOrg = organization.copy(description = genString())

      index.getOrganization(organization.label) shouldReturn Some(metadata.map(_ => organization))
      orgs.update(updatedOrg.label, updatedOrg, 2L).unsafeRunSync() shouldEqual Left(IncorrectRevisionProvided(1L, 2L))
    }

    "reject deprecation when revision is incorrect" in {
      val organization = Organization(genString(), genString())

      index.getOrganization(organization.label) shouldReturn None
      val metadata = orgs.create(organization).unsafeRunSync().right.value

      index.getOrganization(organization.label) shouldReturn Some(metadata.map(_ => organization))
      orgs.deprecate(organization.label, 2L).unsafeRunSync() shouldEqual Left(IncorrectRevisionProvided(1L, 2L))

    }

    "return None if organization doesn't exist" in {
      val label = genString()
      index.getOrganization(label) shouldReturn None
      orgs.fetch(label).unsafeRunSync() shouldEqual None
    }
  }
}

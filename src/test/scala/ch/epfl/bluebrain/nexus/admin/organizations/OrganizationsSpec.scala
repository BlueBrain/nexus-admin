package ch.epfl.bluebrain.nexus.admin.organizations
import java.time.{Clock, Instant, ZoneId}
import java.util.UUID

import akka.actor.ActorSystem
import akka.testkit.TestKit
import cats.effect.{ContextShift, IO}
import ch.epfl.bluebrain.nexus.admin.IOValues
import ch.epfl.bluebrain.nexus.admin.config.AppConfig.HttpConfig
import ch.epfl.bluebrain.nexus.admin.config.Vocabulary.nxv
import ch.epfl.bluebrain.nexus.admin.types.ResourceF
import ch.epfl.bluebrain.nexus.commons.test.Randomness
import ch.epfl.bluebrain.nexus.commons.types.identity.Identity
import ch.epfl.bluebrain.nexus.rdf.syntax.node.unsafe._
import org.scalatest.{EitherValues, Matchers, OptionValues, WordSpecLike}

import scala.concurrent.ExecutionContext

class OrganizationsSpec
    extends TestKit(ActorSystem("OrganizationsSpec"))
    with IOValues
    with WordSpecLike
    with Randomness
    with EitherValues
    with Matchers
    with OptionValues {

  private implicit val clock: Clock          = Clock.fixed(Instant.ofEpochSecond(3600), ZoneId.systemDefault())
  private implicit val http                  = HttpConfig("some", 8080, "/v1", "http://nexus.example.com")
  private implicit val ctx: ContextShift[IO] = IO.contextShift(ExecutionContext.global)
  private implicit val identity              = Identity.Anonymous()
  private val instant                        = clock.instant()
  val orgs                                   = Organizations.inMemory[IO].ioValue

  "Organizations API" should {

    "create and fetch organizations " in {
      val org      = Organization(UUID.randomUUID(), genString(), genString())
      val resource = orgs.create(org).ioValue.right.value

      resource shouldEqual ResourceF.unit(
        url"http://nexus.example.com/v1/orgs/${org.label}".value,
        1L,
        false,
        Set(nxv.Organization),
        instant,
        identity,
        instant,
        identity
      )

      orgs.fetch(org.id).ioValue.value shouldEqual ResourceF(url"http://nexus.example.com/v1/orgs/${org.label}".value,
                                                             1L,
                                                             false,
                                                             Set(nxv.Organization),
                                                             instant,
                                                             identity,
                                                             instant,
                                                             identity,
                                                             org)

      orgs.fetch(UUID.randomUUID()).ioValue shouldEqual None

    }
    "update organization" in {
      val org = Organization(UUID.randomUUID(), genString(), genString())
      orgs.create(org).ioValue.right.value
      val updatedOrg = org.copy(label = genString(), description = genString())

      val resource = orgs.update(updatedOrg, 1L).ioValue.right.value

      resource shouldEqual ResourceF.unit(
        url"http://nexus.example.com/v1/orgs/${updatedOrg.label}".value,
        2L,
        false,
        Set(nxv.Organization),
        instant,
        identity,
        instant,
        identity
      )

      orgs.fetch(org.id).ioValue.value shouldEqual ResourceF(
        url"http://nexus.example.com/v1/orgs/${updatedOrg.label}".value,
        2L,
        false,
        Set(nxv.Organization),
        instant,
        identity,
        instant,
        identity,
        updatedOrg)

    }

    "deprecate organizations" in {
      val org = Organization(UUID.randomUUID(), genString(), genString())
      orgs.create(org).ioValue.right.value

      val resource = orgs.deprecate(org.id, 1L).ioValue.right.value

      resource shouldEqual ResourceF.unit(
        url"http://nexus.example.com/v1/orgs/${org.label}".value,
        2L,
        true,
        Set(nxv.Organization),
        instant,
        identity,
        instant,
        identity
      )

      orgs.fetch(org.id).ioValue.value shouldEqual ResourceF(url"http://nexus.example.com/v1/orgs/${org.label}".value,
                                                             2L,
                                                             true,
                                                             Set(nxv.Organization),
                                                             instant,
                                                             identity,
                                                             instant,
                                                             identity,
                                                             org)

    }
    "fetch organizations by revision" in {
      val org = Organization(UUID.randomUUID(), genString(), genString())
      orgs.create(org).ioValue.right.value
      val updatedOrg = org.copy(label = genString(), description = genString())

      orgs.update(updatedOrg, 1L).ioValue.right.value

      orgs.fetch(org.id, 1L).ioValue.value shouldEqual ResourceF(
        url"http://nexus.example.com/v1/orgs/${org.label}".value,
        1L,
        false,
        Set(nxv.Organization),
        instant,
        identity,
        instant,
        identity,
        org)
    }

    "reject update when revision is incorrect" in {
      val org = Organization(UUID.randomUUID(), genString(), genString())
      orgs.create(org).ioValue.right.value
      val updatedOrg = org.copy(label = genString(), description = genString())

      orgs.update(updatedOrg, 2L).ioValue shouldEqual Left(IncorrectOrganizationRevRejection(1L, 2L))
    }

    "reject deprecation when revision is incorrect" in {
      val org = Organization(UUID.randomUUID(), genString(), genString())
      orgs.create(org).ioValue.right.value

      orgs.deprecate(org.id, 2L).ioValue shouldEqual Left(IncorrectOrganizationRevRejection(1L, 2L))

    }

    "return None if organization doesn't exist" in {
      orgs.fetch(UUID.randomUUID()).ioValue shouldEqual None
    }
  }
}

object OrganizationsSpec {}

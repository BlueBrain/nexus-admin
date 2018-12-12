package ch.epfl.bluebrain.nexus.admin.index

import java.time.Instant
import java.util.UUID

import cats.effect.IO
import ch.epfl.bluebrain.nexus.admin.config.Vocabulary.nxv
import ch.epfl.bluebrain.nexus.admin.organizations.OrganizationEvent.OrganizationCreated
import ch.epfl.bluebrain.nexus.admin.organizations.{Organization, Organizations}
import ch.epfl.bluebrain.nexus.admin.types.ResourceF
import ch.epfl.bluebrain.nexus.commons.test.io.{IOEitherValues, IOOptionValues}
import ch.epfl.bluebrain.nexus.iam.client.types.Identity
import ch.epfl.bluebrain.nexus.rdf.syntax.node.unsafe._
import org.mockito.Mockito.verify
import org.mockito.integrations.scalatest.IdiomaticMockitoFixture
import org.scalatest.{Matchers, WordSpecLike}

class OrganizationsIndexerSpec
    extends WordSpecLike
    with IdiomaticMockitoFixture
    with Matchers
    with IOEitherValues
    with IOOptionValues {

  trait Context {
    val instant = Instant.now
    val types   = Set(nxv.Project.value)
    val caller  = Identity.User("realm", "alice")
    val orgId   = UUID.randomUUID
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

    val orgs: Organizations[IO]              = mock[Organizations[IO]]
    val index: Index[IO]                     = mock[Index[IO]]
    val orgIndexer: OrganizationsIndexer[IO] = new OrganizationsIndexer[IO](orgs, index)
  }

  "Organizations indexer" should {

    "index organizations" in new Context {

      orgs.fetch(organization.uuid) shouldReturn IO.pure(Some(organization))
      index.updateOrganization(organization) shouldReturn IO.pure(true)

      orgIndexer
        .index(
          List(
            OrganizationCreated(organization.uuid, 1L, organization.value, instant, caller),
          ))
        .unsafeRunSync()

      verify(index).updateOrganization(organization)
    }
  }

}

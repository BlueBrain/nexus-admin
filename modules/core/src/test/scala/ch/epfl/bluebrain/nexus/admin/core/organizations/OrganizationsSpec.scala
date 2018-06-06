package ch.epfl.bluebrain.nexus.admin.core.organizations

import java.util.UUID

import akka.actor.ActorSystem
import akka.testkit.{DefaultTimeout, TestKit}
import cats.instances.future._
import ch.epfl.bluebrain.nexus.admin.core.CallerCtx._
import ch.epfl.bluebrain.nexus.admin.core.Fault.CommandRejected
import ch.epfl.bluebrain.nexus.admin.core.TestHelper
import ch.epfl.bluebrain.nexus.admin.core.config.AppConfig.OrganizationsConfig
import ch.epfl.bluebrain.nexus.admin.core.resources.ResourceRejection
import ch.epfl.bluebrain.nexus.admin.core.resources.ResourceRejection.{
  IncorrectRevisionProvided,
  ResourceDoesNotExists,
  ResourceIsDeprecated
}
import ch.epfl.bluebrain.nexus.admin.core.resources.ResourceState._
import ch.epfl.bluebrain.nexus.admin.core.types.Ref._
import ch.epfl.bluebrain.nexus.admin.core.types.RefVersioned
import ch.epfl.bluebrain.nexus.admin.refined.organization.OrganizationReference
import ch.epfl.bluebrain.nexus.commons.http.JsonOps._
import ch.epfl.bluebrain.nexus.commons.shacl.validator.{ImportResolver, ShaclValidator}
import ch.epfl.bluebrain.nexus.commons.sparql.client.SparqlClient
import ch.epfl.bluebrain.nexus.commons.types.identity.Identity.Anonymous
import ch.epfl.bluebrain.nexus.iam.client.Caller.AnonymousCaller
import ch.epfl.bluebrain.nexus.sourcing.mem.MemoryAggregate
import ch.epfl.bluebrain.nexus.sourcing.mem.MemoryAggregate._
import eu.timepit.refined.auto._
import io.circe.Json
import org.scalatest._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar

import scala.concurrent.Future
import scala.concurrent.duration._

class OrganizationsSpec
    extends TestKit(ActorSystem("OrganizationsSpec"))
    with DefaultTimeout
    with WordSpecLike
    with Matchers
    with TryValues
    with ScalaFutures
    with TestHelper
    with MockitoSugar
    with OptionValues
    with CancelAfterFailure {

  private implicit val caller: AnonymousCaller = AnonymousCaller(Anonymous())
  private implicit val config: OrganizationsConfig =
    OrganizationsConfig(3 seconds, "https://nexus.example.ch/v1/orgs/")
  private implicit val ec                              = system.dispatcher
  private val orgsAggregate                            = MemoryAggregate("organizations")(Initial, next, Eval().apply).toF[Future]
  private val cl                                       = mock[SparqlClient[Future]]
  implicit val schaclValidator: ShaclValidator[Future] = ShaclValidator(ImportResolver.noop[Future])
  private val organizations                            = Organizations(orgsAggregate, cl)

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(5 seconds, 100 milliseconds)

  "An Organization bundle" should {
    trait Context {
      val id: OrganizationReference = genOrgReference()
      val value: Json               = genOrganizationValue()
    }

    "create new organization" in new Context {

      organizations.create(id, value).futureValue shouldEqual RefVersioned(id, 1L)
      val org = organizations.fetch(id).futureValue.get
      org.id.value shouldEqual id
      org.uuid should fullyMatch regex """[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"""
      org.value shouldEqual value
    }

    "prevent creating an organization without a name" in new Context {
      organizations
        .create(id, value.removeKeys("name"))
        .failed
        .futureValue
        .asInstanceOf[CommandRejected]
        .rejection shouldBe a[ResourceRejection.ShapeConstraintViolations]
    }
    "update an organization" in new Context {
      organizations.create(id, value).futureValue shouldEqual RefVersioned(id, 1L)
      val org = organizations.fetch(id).futureValue.get

      val updatedName = genString()
      val update      = org.value.updateField("name", updatedName)
      organizations.update(id, 1, update).futureValue shouldEqual RefVersioned(id, 2L)

      val updatedOrg = organizations.fetch(id).futureValue.get

      updatedOrg.value.getString("name") shouldEqual updatedName
    }

    "deprecate an organization" in new Context {
      organizations.create(id, value).futureValue shouldEqual RefVersioned(id, 1L)
      organizations.deprecate(id, 1L).futureValue shouldEqual RefVersioned(id, 2L)
      organizations.fetch(id).futureValue.get.deprecated shouldEqual true

    }
    "fetch old revision of an organization" in new Context {
      organizations.create(id, value).futureValue shouldEqual RefVersioned(id, 1L)
      val org = organizations.fetch(id).futureValue.get

      val oldName     = value.getString("name")
      val updatedName = genString()
      val update      = org.value.updateField("name", updatedName)
      organizations.update(id, 1, update).futureValue shouldEqual RefVersioned(id, 2L)

      organizations.fetch(id).futureValue.get.value.getString("name") shouldEqual updatedName
      organizations.fetch(id, 1L).futureValue.get.value.getString("name") shouldEqual oldName
      organizations.fetch(id, 2L).futureValue.get.value.getString("name") shouldEqual updatedName

    }
    "retun None when fetching a revision which doesn't exist" in new Context {
      organizations.create(id, value).futureValue shouldEqual RefVersioned(id, 1L)
      organizations.fetch(id, 100L).futureValue shouldEqual None
    }
    "prevent double deprecations" in new Context {
      organizations.create(id, value).futureValue shouldEqual RefVersioned(id, 1L)
      organizations.deprecate(id, 1L).futureValue shouldEqual RefVersioned(id, 2L)
      organizations.deprecate(id, 2L).failed.futureValue shouldEqual CommandRejected(ResourceIsDeprecated)

    }
    "prevent updating a non  existing organization" in new Context {
      organizations
        .update(id, 1L, value.updateField("_uuid", UUID.randomUUID().toString))
        .failed
        .futureValue shouldEqual CommandRejected(ResourceDoesNotExists)

    }
    "prevent updating with incorrect rev" in new Context {
      organizations.create(id, value).futureValue shouldEqual RefVersioned(id, 1L)
      organizations
        .update(id, 2L, value.updateField("_uuid", UUID.randomUUID().toString))
        .failed
        .futureValue shouldEqual CommandRejected(IncorrectRevisionProvided)

    }

    "prevent deprecation with incorrect rev" in new Context {
      organizations.create(id, value).futureValue shouldEqual RefVersioned(id, 1L)
      organizations.deprecate(id, 2L).failed.futureValue shouldEqual CommandRejected(IncorrectRevisionProvided)
    }

  }
}

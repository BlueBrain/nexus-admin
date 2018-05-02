package ch.epfl.bluebrain.nexus.admin.service.indexing

import java.time.Instant

import akka.actor.{ActorSystem, Props}
import akka.testkit.{DefaultTimeout, TestKit}
import ch.epfl.bluebrain.nexus.commons.sparql.client.{InMemorySparqlActor, InMemorySparqlClient}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import cats.instances.future._
import ch.epfl.bluebrain.nexus.admin.core.config.AppConfig.ProjectsConfig
import ch.epfl.bluebrain.nexus.admin.core.projects.Project.{Config, LoosePrefixMapping, ProjectValue}
import ch.epfl.bluebrain.nexus.admin.core.resources.ResourceEvent.{ResourceCreated, ResourceDeprecated, ResourceUpdated}
import ch.epfl.bluebrain.nexus.admin.core.types.Ref._
import eu.timepit.refined.auto._
import ch.epfl.bluebrain.nexus.admin.ld.Const.{nxv, rdf}
import ch.epfl.bluebrain.nexus.admin.ld.PrefixMapping.randomPrefix
import ch.epfl.bluebrain.nexus.admin.refined.project.ProjectReference
import ch.epfl.bluebrain.nexus.commons.test.Randomness
import ch.epfl.bluebrain.nexus.admin.refined.project._
import ch.epfl.bluebrain.nexus.commons.types.Meta
import ProjectValue._
import akka.http.scaladsl.model.Uri
import ch.epfl.bluebrain.nexus.admin.refined.ld.Id
import ch.epfl.bluebrain.nexus.commons.types.identity.Identity
import eu.timepit.refined.api.RefType.refinedRefType
import io.circe.syntax._

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import ch.epfl.bluebrain.nexus.admin.ld.Const._

class ResourceSparqlIndexerSpec
    extends TestKit(ActorSystem("ResourceSparqlIndexerSpec"))
    with DefaultTimeout
    with WordSpecLike
    with Matchers
    with ScalaFutures
    with BeforeAndAfterAll
    with Randomness {

  override implicit val patienceConfig: PatienceConfig =
    PatienceConfig(5 seconds, 100 milliseconds)

  override protected def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
    super.afterAll()
  }

  def genProjectValue(): ProjectValue = {
    val prefixMappings = List(
      LoosePrefixMapping(randomPrefix(), refinedRefType.unsafeRewrap(nxv.namespaceBuilder)),
      LoosePrefixMapping(randomPrefix(), refinedRefType.unsafeRewrap(rdf.namespaceBuilder))
    )
    ProjectValue(genString(), Some(genString()), prefixMappings, Config(genInt().toLong))
  }

  def genReference(length: Int = 9): ProjectReference =
    refinedRefType.unsafeWrap(genString(length = length, Vector.range('a', 'z') ++ Vector.range('0', '9')))

  def projectTriples(id: Id,
                     createdAt: Instant,
                     updatedAt: Instant,
                     pv: ProjectValue,
                     rev: Long,
                     deprecated: Boolean = false): List[(String, String, String)] = List(
    (id.value, nxv.createdAtTime.value, createdAt.toString),
    (id.value, nxv.updatedAtTime.value, updatedAt.toString),
    (id.value, nxv.rev.value, rev.toString),
    (id.value, nxv.description.value, pv.description.get),
    (id.value, rdf.tpe.value, nxv.Project.value),
    (id.value, nxv.deprecated.value, deprecated.toString)
  )

  "ResourceSparqlIndexer" should {

    implicit val ec   = system.dispatcher
    val inMemoryActor = system.actorOf(Props[InMemorySparqlActor]())
    val cl            = InMemorySparqlClient(inMemoryActor)
    val indexer       = ResourceSparqlIndexer(cl)
    val meta          = Meta(Identity.Anonymous(), Instant.now())

    "index resource created event" in new Fixture {
      val projectValue: ProjectValue = genProjectValue()
      val event                      = ResourceCreated(id, 1L, meta, Set.empty, projectValue.asJson)
      indexer.index(event).futureValue

      cl.triples(id.value) should have size 15
      cl.triples(id.value) should contain allElementsOf projectTriples(id, meta.instant, meta.instant, projectValue, 1L)
    }

    "index resource updated event" in new Fixture {
      val projectValue: ProjectValue = genProjectValue()
      val event                      = ResourceCreated(id, 1L, meta, Set.empty, projectValue.asJson)
      indexer.index(event).futureValue

      cl.triples(id.value) should have size 15
      cl.triples(id.value) should contain allElementsOf projectTriples(id, meta.instant, meta.instant, projectValue, 1L)

      val updatedProject = projectValue.copy(description = Some(genString()))
      val updatedMeta    = meta.copy(instant = meta.instant.plusSeconds(60L))
      val updateEvent    = ResourceUpdated(id, 2L, updatedMeta, Set.empty, updatedProject.asJson)
      indexer.index(updateEvent).futureValue

      cl.triples(id.value) should have size 15
      cl.triples(id.value) should contain allElementsOf projectTriples(id,
                                                                       meta.instant,
                                                                       updatedMeta.instant,
                                                                       updatedProject,
                                                                       2)
    }

    "index resource deprecated event" in new Fixture {
      val projectValue: ProjectValue = genProjectValue()
      val event                      = ResourceCreated(id, 1L, meta, Set.empty, projectValue.asJson)
      indexer.index(event).futureValue

      cl.triples(id.value) should have size 15
      cl.triples(id.value) should contain allElementsOf projectTriples(id, meta.instant, meta.instant, projectValue, 1L)

      val deprecateMeta  = meta.copy(instant = meta.instant.plusSeconds(60L))
      val deprecateEvent = ResourceDeprecated(id, 2L, deprecateMeta, Set.empty)
      indexer.index(deprecateEvent).futureValue

      cl.triples(id.value) should have size 15
      cl.triples(id.value) should contain allElementsOf projectTriples(id,
                                                                       meta.instant,
                                                                       deprecateMeta.instant,
                                                                       projectValue,
                                                                       2,
                                                                       deprecated = true)
    }

    implicit class InMemorySparqlClientOps(cl: InMemorySparqlClient)(implicit ec: ExecutionContext) {
      private def triplesFor(query: String): Future[List[(String, String, String)]] =
        cl.query(query).map { rs =>
          rs.asScala.toList.map { qs =>
            val obj = {
              val node = qs.get("?o")
              if (node.isLiteral) node.asLiteral().getLexicalForm
              else node.asResource().toString
            }
            (qs.get("?s").toString, qs.get("?p").toString, obj)
          }
        }

      def triples(graph: Uri): List[(String, String, String)] =
        triplesFor(s"SELECT * WHERE { GRAPH <$graph> { ?s ?p ?o } }").futureValue

      def triples(): List[(String, String, String)] =
        triplesFor("SELECT * WHERE {GRAPH ?g { ?s ?p ?o }}").futureValue
    }
  }

  trait Fixture {
    implicit val config: ProjectsConfig = ProjectsConfig(3 seconds, "https://nexus.example.ch/v1/projects/", 100000L)
    val idResolvable                    = refToResolvable
    val id: Id                          = idResolvable(genReference())
    val prefixMappings = List(
      LoosePrefixMapping(randomPrefix(), refinedRefType.unsafeRewrap(nxv.namespaceBuilder)),
      LoosePrefixMapping(randomPrefix(), refinedRefType.unsafeRewrap(rdf.namespaceBuilder))
    )

  }

}

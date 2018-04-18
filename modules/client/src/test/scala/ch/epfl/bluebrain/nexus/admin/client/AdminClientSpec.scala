package ch.epfl.bluebrain.nexus.admin.client

import akka.actor.ActorSystem
import akka.http.scaladsl.model.Uri.Path
import akka.http.scaladsl.model._
import akka.stream.{ActorMaterializer, Materializer}
import akka.testkit.TestKit
import ch.epfl.bluebrain.nexus.admin.client.config.AdminConfig
import ch.epfl.bluebrain.nexus.admin.refined.project.ProjectReference
import ch.epfl.bluebrain.nexus.commons.http.HttpClient.UntypedHttpClient
import ch.epfl.bluebrain.nexus.commons.iam.acls.{FullAccessControl, Permission, Permissions}
import ch.epfl.bluebrain.nexus.commons.iam.identity.Identity.UserRef
import ch.epfl.bluebrain.nexus.commons.test.Resources.contentOf
import eu.timepit.refined.auto._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._

class AdminClientSpec
    extends TestKit(ActorSystem("IamClientSpec"))
    with WordSpecLike
    with Matchers
    with ScalaFutures
    with BeforeAndAfterAll {

  private val base = Uri("http://localhost/v0/projects/")

  private implicit val config: AdminConfig  = AdminConfig(base)
  private implicit val ec: ExecutionContext = system.dispatcher
  private implicit val mt: Materializer     = ActorMaterializer()

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(6.seconds, 300.milliseconds)

  override protected def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
    super.afterAll()
  }

  "An AdminClient" should {
    "return an existing project from upstream" in {
      val name: ProjectReference = "22g4jpv25hox63s"
      val mockedResponse = Future.successful(
        HttpResponse(
          entity = HttpEntity(ContentTypes.`application/json`, contentOf("/project.json")),
          status = StatusCodes.OK
        ))
      implicit val cl: UntypedHttpClient[Future] =
        mockedClient(base.copy(path = base.path + "22g4jpv25hox63s"), mockedResponse)
      val adminClient = AdminClient(config)

      val project = adminClient.getProject(name, None).futureValue
      project.deprecated shouldEqual false
      project.rev shouldEqual 3
      project.name shouldEqual name
    }

    "return an existing project ACLs from upstream" in {
      val name: ProjectReference = "22g4jpv25hox63s"
      val mockedResponse = Future.successful(
        HttpResponse(
          entity = HttpEntity(ContentTypes.`application/json`, contentOf("/project-acls.json")),
          status = StatusCodes.OK
        ))
      implicit val cl: UntypedHttpClient[Future] =
        mockedClient(base.copy(path = base.path + "22g4jpv25hox63s/acls"), mockedResponse)
      val adminClient = AdminClient(config)

      val project = adminClient.getProjectAcls(name, None).futureValue
      project.acl shouldEqual List(
        FullAccessControl(UserRef("bbp-test", "ca88f6d1-4f71-4fc0-b023-de82b8afdc30"), Path("22g4jpv25hox63s"), Permissions(Permission("projects/read")))
      )
    }
  }

  def mockedClient(expectedUri: Uri, response: Future[HttpResponse]): UntypedHttpClient[Future] =
    new UntypedHttpClient[Future] {
      override def apply(req: HttpRequest): Future[HttpResponse] =
        if (req.uri == expectedUri) response
        else fail(s"Wrong request uri: ${req.uri} expected: $expectedUri")

      override def discardBytes(entity: HttpEntity): Future[HttpMessage.DiscardedEntity] =
        Future.apply(entity.discardBytes())

      override def toString(entity: HttpEntity): Future[String] = Future.apply("")
    }
}

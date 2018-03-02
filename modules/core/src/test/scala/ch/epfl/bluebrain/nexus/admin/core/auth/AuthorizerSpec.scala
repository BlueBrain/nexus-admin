package ch.epfl.bluebrain.nexus.admin.core.auth

import java.time.Clock

import akka.actor.ActorSystem
import akka.cluster.Cluster
import akka.http.scaladsl.model.headers.OAuth2BearerToken
import akka.testkit.TestKit
import ch.epfl.bluebrain.nexus.admin.core.CallerCtx
import ch.epfl.bluebrain.nexus.admin.core.CallerCtx._
import ch.epfl.bluebrain.nexus.admin.core.auth.Authorizer.AuthorizeError
import ch.epfl.bluebrain.nexus.commons.iam.IamClient
import ch.epfl.bluebrain.nexus.commons.iam.acls.Path._
import ch.epfl.bluebrain.nexus.commons.iam.acls.Permission._
import ch.epfl.bluebrain.nexus.commons.iam.acls.{FullAccessControlList, Permissions}
import ch.epfl.bluebrain.nexus.commons.iam.auth.AuthenticatedUser
import ch.epfl.bluebrain.nexus.commons.iam.identity.Caller.{AnonymousCaller, AuthenticatedCaller}
import ch.epfl.bluebrain.nexus.commons.iam.identity.Identity.{Anonymous, UserRef}
import ch.epfl.bluebrain.nexus.commons.test.Randomness
import ch.epfl.bluebrain.nexus.sourcing.akka.cache.ShardedCache.CacheSettings
import org.mockito.Mockito
import org.mockito.Mockito._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{BeforeAndAfter, BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.concurrent.Future
import scala.concurrent.duration._

class AuthorizerSpec
    extends TestKit(ActorSystem("AuthorizerSpec"))
    with WordSpecLike
    with Matchers
    with MockitoSugar
    with ScalaFutures
    with BeforeAndAfterAll
    with BeforeAndAfter
    with Randomness {

  private val client: IamClient[Future] = mock[IamClient[Future]]

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    Cluster(system).join(Cluster(system).selfAddress)
  }

  override protected def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
    super.afterAll()
  }

  before {
    Mockito.reset(client)
  }

  override implicit def patienceConfig: PatienceConfig = PatienceConfig(6 seconds, 300 milliseconds)

  private implicit def toToken(implicit ctx: CallerCtx): Option[OAuth2BearerToken] = ctx.caller.credentials

  "An Authorizer" should {

    implicit val conf: CacheSettings   = CacheSettings(5 minutes, 3 seconds, 100)
    val authorizer: Authorizer[Future] = Authorizer(client)
    val identity                       = Anonymous()
    implicit val anon: AnonymousCaller = AnonymousCaller(identity)
    val caller =
      AuthenticatedCaller(OAuth2BearerToken(genString()), AuthenticatedUser(Set(UserRef("realm", genString()))))
    implicit val clock: Clock = Clock.systemUTC

    "has access when the path has the provided permissions" in {
      val path = "a" / "b"
      val acl  = FullAccessControlList((identity, path, Permissions(Read, Write)))
      when(client.getAcls(path, parents = true, self = true)).thenReturn(Future.successful(acl))
      authorizer.validateAccess(path, Read).futureValue shouldEqual (())
    }

    "does not have access when the path does not have the provided permissions" in {
      val path = "a" / "b"
      whenReady(authorizer.validateAccess(path, Own).failed){ e =>
        e shouldEqual AuthorizeError.UnmatchedPermission(Own)
      }
    }

    "get acls for a cached path and token" in {
      val path = "a" / "b"
      val acl  = FullAccessControlList((identity, path, Permissions(Read, Write)))
      authorizer.acls(path).futureValue shouldEqual acl
    }

    "fail to get acls for an uncached path and token" in {
      val path = "a" / "b"
      whenReady(authorizer.acls(path)(CallerCtx(clock, caller)).failed) { e =>
        e shouldBe a[AuthorizeError.Unexpected]
      }
    }

    "get acls for an uncached path & token through the IAM client" in {
      val path = "a" / "b"
      val acl  = FullAccessControlList((identity, path, Permissions(Read, Own)))
      when(client.getAcls(path, parents = true, self = true)(caller.credentials)).thenReturn(Future.successful(acl))
      authorizer.acls(path)(CallerCtx(clock, caller)).futureValue shouldEqual acl
    }
  }
}

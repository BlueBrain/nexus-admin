package ch.epfl.bluebrain.nexus.admin.core.auth

import java.time.Clock

import ch.epfl.bluebrain.nexus.admin.core.CallerCtx
import ch.epfl.bluebrain.nexus.iam.client.Caller.AuthenticatedCaller
import ch.epfl.bluebrain.nexus.commons.types.identity.Identity
import ch.epfl.bluebrain.nexus.commons.types.identity.Identity.{Anonymous, AuthenticatedRef, GroupRef, UserRef}
import ch.epfl.bluebrain.nexus.commons.test.Randomness
import org.scalatest.{Matchers, WordSpecLike}

class CallerCtxSpec extends WordSpecLike with Matchers with Randomness {

  "A CallerCtx" should {
    val subject        = UserRef("realm", genString())
    val authenticated  = AuthenticatedRef(Some("realm"))
    val group          = GroupRef("realm", genString())
    implicit val clock = Clock.systemUTC

    "fetch the subject" in {
      val ctx = CallerCtx(AuthenticatedCaller(None, Set(group, subject, authenticated, Anonymous())))
      ctx.meta.author shouldEqual subject
    }

    "fetch the authenticated ref" in {
      val ctx = CallerCtx(AuthenticatedCaller(None, Set(group, authenticated, Anonymous())))
      ctx.meta.author shouldEqual authenticated
    }

    "fetch anonymous" in {
      val ctx = CallerCtx(AuthenticatedCaller(None, Set[Identity](group)))
      ctx.meta.author shouldEqual Anonymous()
    }
  }

}

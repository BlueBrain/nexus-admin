package ch.epfl.bluebrain.nexus.admin.core.syntax

import java.time.{Clock, Instant, ZoneId}

import ch.epfl.bluebrain.nexus.commons.test.Randomness
import ch.epfl.bluebrain.nexus.iam.client.Caller.{AnonymousCaller, AuthenticatedCaller}
import ch.epfl.bluebrain.nexus.iam.client.types.AuthToken
import ch.epfl.bluebrain.nexus.iam.client.types.Identity._
import ch.epfl.bluebrain.nexus.commons.types.identity.{Identity => Ident}
import org.scalatest.{Matchers, WordSpecLike}
import ch.epfl.bluebrain.nexus.admin.core.syntax.caller._
import ch.epfl.bluebrain.nexus.commons.types.Meta

class CallerSyntaxSpec extends WordSpecLike with Matchers with Randomness {

  "A Caller" should {
    val user           = UserRef("realm", genString())
    val group          = GroupRef("realm", genString())
    val caller         = AuthenticatedCaller(AuthToken("valid"), user, Set(group, user, Anonymous))
    val anonCaller     = AnonymousCaller
    implicit val clock = Clock.fixed(Instant.ofEpochSecond(3600), ZoneId.systemDefault())

    "fetch the meta for a user caller" in {
      caller.meta shouldEqual Meta(Ident.UserRef(user.realm, user.sub), clock.instant())
    }

    "fetch the meta for anonymous caller" in {
      anonCaller.meta shouldEqual Meta(Ident.Anonymous(), clock.instant())
    }
  }

}

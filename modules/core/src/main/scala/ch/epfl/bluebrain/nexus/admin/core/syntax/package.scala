package ch.epfl.bluebrain.nexus.admin.core

import java.time.Clock

import ch.epfl.bluebrain.nexus.commons.types.Meta
import ch.epfl.bluebrain.nexus.iam.client.Caller
import ch.epfl.bluebrain.nexus.iam.client.Caller.{AnonymousCaller, AuthenticatedCaller}
import ch.epfl.bluebrain.nexus.commons.types.identity.{Identity => Ident}

package object syntax {

  object caller {

    /**
      * Syntax sugar to expose methods on type [[Caller]]
      */
    implicit class CallerSyntax(caller: Caller) {

      /**
        * @return the [[Meta]] information obtained form the caller and the implicitly
        *         available ''clock''
        */
      def meta(implicit clock: Clock) = Meta(identity(), clock.instant())

      private def identity(): Ident =
        caller match {
          case AnonymousCaller                 => Ident.Anonymous()
          case AuthenticatedCaller(_, user, _) => Ident.UserRef(user.realm, user.sub)
        }
    }
  }

}

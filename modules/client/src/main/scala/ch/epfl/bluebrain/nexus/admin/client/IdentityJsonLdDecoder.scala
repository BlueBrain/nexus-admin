package ch.epfl.bluebrain.nexus.admin.client

import ch.epfl.bluebrain.nexus.commons.types.identity.Identity
import io.circe.Decoder
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.semiauto._

trait ConfigInstance {
  private[client] implicit val config: Configuration =
    Configuration.default
      .withDiscriminator("@type")
      .copy(transformMemberNames = {
        case "id"  => "@id"
        case other => other
      })
}
trait IdentityJsonLdDecoder extends ConfigInstance {

  /**
    * Identity decoder which converts JSON-LD representation to ''Identity''
    */
  implicit def identityDecoder: Decoder[Identity] = deriveDecoder[Identity]
}

object IdentityJsonLdDecoder extends IdentityJsonLdDecoder

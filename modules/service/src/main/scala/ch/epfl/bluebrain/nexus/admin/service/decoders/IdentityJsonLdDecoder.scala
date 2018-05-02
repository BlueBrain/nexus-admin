package ch.epfl.bluebrain.nexus.admin.service.decoders

import ch.epfl.bluebrain.nexus.commons.types.identity.Identity
import io.circe.Decoder
import io.circe.generic.extras.Configuration

trait ConfigInstance {
  private[decoders] implicit val config: Configuration =
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
  implicit def identityDecoder: Decoder[Identity] = {
    import io.circe.generic.extras.semiauto._
    deriveDecoder[Identity]
  }
}

object IdentityJsonLdDecoder extends IdentityJsonLdDecoder

package ch.epfl.bluebrain.nexus.admin.ld

import ch.epfl.bluebrain.nexus.admin.refined.ld.Id
import ch.epfl.bluebrain.nexus.admin.ld.PrefixMapping.randomPrefix
import eu.timepit.refined.api.RefType.applyRef
import io.circe.{Decoder, Encoder}

object IdOps {

  /**
    * Syntax sugar to expose methods on type [[Id]]
    */
  implicit class IdSyntax(value: Id) {

    /**
      * Converts a [[Id]] to an [[IdRef]] with a random ''prefix''
      */
    def toId: IdRef = {
      val (namespace, reference) = value.decompose
      IdRef(randomPrefix(), namespace, reference)
    }

  }

  final implicit val IdDecoder: Decoder[Id] =
    Decoder.decodeString.emap(applyRef[Id](_))
  final implicit val IdEncoder: Encoder[Id] =
    Encoder.encodeString.contramap(_.value)

}

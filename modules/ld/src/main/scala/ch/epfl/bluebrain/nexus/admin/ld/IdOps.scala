package ch.epfl.bluebrain.nexus.admin.ld

import ch.epfl.bluebrain.nexus.admin.refined.ld.Id
import ch.epfl.bluebrain.nexus.admin.ld.PrefixMapping.randomPrefixName
import eu.timepit.refined.api.RefType.applyRef
import io.circe.{Decoder, Encoder}

object IdOps {

  /**
    * Syntax sugar to expose methods on type [[Id]]
    */
  implicit class IdSyntax(value: Id) {

    /**
      * Converts a [[Id]] to an [[IdRef]] with a random ''prefixName''
      */
    def toId: IdRef = {
      val (prefixValue, reference) = value.decompose
      IdRef(randomPrefixName(), prefixValue, reference)
    }

  }

  final implicit val IdDecoder: Decoder[Id] =
    Decoder.decodeString.emap(applyRef[Id](_))
  final implicit val IdEncoder: Encoder[Id] =
    Encoder.encodeString.contramap(_.value)

}

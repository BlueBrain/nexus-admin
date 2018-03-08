package ch.epfl.bluebrain.nexus.admin.ld

import ch.epfl.bluebrain.nexus.admin.refined.ld.DecomposableId
import ch.epfl.bluebrain.nexus.admin.ld.Prefix.randomPrefixName
import eu.timepit.refined.api.RefType.applyRef
import io.circe.{Decoder, Encoder}

object DecomposableIdOps {

  /**
    * Syntax sugar to expose methods on type [[DecomposableId]]
    */
  implicit class DecomposableIdSyntax(value: DecomposableId) {

    /**
      * Converts a [[DecomposableId]] to an [[IdRef]] with a random ''prefixName''
      */
    def toId: IdRef = {
      val (prefixValue, reference) = value.decompose
      IdRef(randomPrefixName(), prefixValue, reference)
    }

  }

  final implicit val decomposableIdDecoder: Decoder[DecomposableId] =
    Decoder.decodeString.emap(applyRef[DecomposableId](_))
  final implicit val decomposableIdEncoder: Encoder[DecomposableId] =
    Encoder.encodeString.contramap(_.value)

}

package ch.epfl.bluebrain.nexus.admin.refined

import com.github.ghik.silencer.silent
import eu.timepit.refined.api.RefType.applyRef
import eu.timepit.refined.api.{Refined, Validate}
import io.circe.{Decoder, Encoder}
import io.circe.syntax._

object circe extends CirceInstances

@SuppressWarnings(Array("UnusedMethodParameter"))
private[refined] trait CirceInstances {

  @silent
  implicit final def encoderOfRefinedType[FTP, T: Encoder, P](implicit ev: Refined[T, P] =:= FTP,
                                                              V: Validate[T, P]): Encoder[Refined[T, P]] =
    Encoder.encodeJson.contramap(v => v.value.asJson)

  implicit final def decoderOfRefinedType[FTP, T, P](implicit ev: Refined[T, P] =:= FTP,
                                                     V: Validate[T, P],
                                                     dec: Decoder[T]): Decoder[FTP] =
    Decoder.decodeJson.emap(v => dec.apply(v.hcursor).left.map(_.message).flatMap(v => applyRef[FTP](v)))
}

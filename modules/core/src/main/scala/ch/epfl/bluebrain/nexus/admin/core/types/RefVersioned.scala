package ch.epfl.bluebrain.nexus.admin.core.types

import ch.epfl.bluebrain.nexus.admin.ld.Const._
import ch.epfl.bluebrain.nexus.admin.ld.IdResolvable
import io.circe.syntax._
import io.circe.{Encoder, Json}

/**
  * A versioned reference of a ''resource''
  *
  * @param id  the reference value
  * @param rev the selected revision of the resource
  * @tparam A the generic type of the id's ''reference''
  */
final case class RefVersioned[A](id: Ref[A], rev: Long) extends Versioned

object RefVersioned {

  final implicit def refRevisionEncoder[A: IdResolvable]: Encoder[RefVersioned[A]] =
    Encoder.encodeJson.contramap {
      case RefVersioned(id, rev) => Json.obj(`@id` -> id.asJson, nxv.rev.reference.value -> Json.fromLong(rev))
    }
}

package ch.epfl.bluebrain.nexus.admin.query

import cats.Show
import ch.epfl.bluebrain.nexus.admin.ld.Const.nxv
import ch.epfl.bluebrain.nexus.admin.refined.ld.DecomposableId
import eu.timepit.refined.api.RefType.applyRef

/**
  * Type representing the field to return from a query
  *
  * @param value the field value
  */
final case class Field(value: DecomposableId)

object Field {
  val All                             = Field(nxv.allFields.id)
  implicit val showField: Show[Field] = Show.show(_.value.value)

  implicit def fromString(value: String): Either[String, Field] =
    applyRef[DecomposableId](value).map(Field.apply)
}

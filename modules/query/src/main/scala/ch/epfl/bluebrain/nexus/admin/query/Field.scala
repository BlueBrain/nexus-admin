package ch.epfl.bluebrain.nexus.admin.query

import cats.Show
import ch.epfl.bluebrain.nexus.admin.ld.Const.nxv
import ch.epfl.bluebrain.nexus.admin.refined.ld.Id
import eu.timepit.refined.api.RefType.applyRef

/**
  * Type representing the field to return from a query
  *
  * @param value the field value
  */
final case class Field(value: Id)

object Field {
  val All                             = Field(nxv.allFields.id)
  implicit val showField: Show[Field] = Show.show(_.value.value)

  implicit def fromString(value: String): Either[String, Field] =
    applyRef[Id](value).map(Field.apply)
}

package ch.epfl.bluebrain.nexus.admin.ld

import ch.epfl.bluebrain.nexus.admin.ld.JsonLD.IdType
import ch.epfl.bluebrain.nexus.admin.refined.ld.{DecomposableId, Id}
import io.circe.Json
import shapeless.Typeable

/**
  * A data type representing possible JSON-LD values.
  */
trait JsonLD {

  /**
    * The underlying [[Json]] data type
    */
  def json: Json

  /**
    * The optionally available root ''subject'' of the JSON-LD
    */
  def id: Option[IdType]

  /**
    * The optionally available root ''rdf:type'' of the JSON-LD
    */
  def tpe: Option[IdRef]

  /**
    * Attempt to fetch the object for the given predicate ''uri''
    *
    * @param uri the given predicate
    * @tparam T the generic type of the desired object
    */
  def predicate[T: Typeable](uri: Id): Option[T]

  /**
    * Attempt to fetch the object for the given predicate ''idRef''
    *
    * @param idRef the given predicate
    * @tparam T the generic type of the desired object
    */
  def predicate[T: Typeable](idRef: IdRef): Option[T] = predicate(idRef.id)

  /**
    * Perform a deep merge of this JSON value with another JSON value.
    *
    * Objects are merged by key, values from the argument JSON take
    * precedence over values from this JSON. Nested objects are
    * recursed.
    */
  def deepMerge(other: Json): JsonLD

  /**
    * Perform a deep merge of this JSON value with another JSON value.
    *
    * Objects are merged by key, values from the argument JSON take
    * precedence over values from this JSON. Nested objects are
    * recursed.
    */
  def deepMerge(otherLD: JsonLD): JsonLD = deepMerge(otherLD.json)
}

object JsonLD {

  final def apply(json: Json): JsonLD = JenaJsonLD(json)

  implicit def toJson(value: JsonLD): Json = value.json

  /**
    * Base enumeration type for IdType classes.
    */
  sealed trait IdType extends Product with Serializable

  /**
    * An empty ''id'' due to graph recursion
    */
  final case object Empty extends IdType

  /**
    * An id which is a [[DecomposableId]] (uri)
    *
    * @param value the uri Id
    */
  final case class IdTypeUri(value: DecomposableId) extends IdType

  /**
    * An id which is a Blank Node (does not have a uri)
    *
    * @param value the blank node auto-generated string
    */
  final case class IdTypeBlank(value: String) extends IdType

}

package ch.epfl.bluebrain.nexus.admin.ld

import ch.epfl.bluebrain.nexus.admin.ld.JsonLD.IdType
import ch.epfl.bluebrain.nexus.admin.refined.ld._
import eu.timepit.refined.auto._
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

  /**
    * Attempt to fetch the [[PrefixValue]] for a given ''prefixName''.
    * It will look up into the JSON-LD @context object whether the ''prefixName'' key exists, and it will return the value associated to it.
    *
    * @param prefixName the prefix name to look up into the @context object
    */
  def prefixValueOf(prefixName: PrefixName): Option[PrefixValue]

  /**
    * Attempt to fetch the [[PrefixName]] for a given ''prefixValue''.
    * It will look up into the JSON-LD @context object whether the ''prefixValue'' value exists, and it will return the key associated to it.
    *
    * @param prefixValue the prefix value to look up into the @context object
    */
  def prefixNameOf(prefixValue: PrefixValue): Option[PrefixName]
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

package ch.epfl.bluebrain.nexus.admin.ld

import ch.epfl.bluebrain.nexus.admin.ld.JsonLD.IdType
import ch.epfl.bluebrain.nexus.admin.refined.ld._
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
    * Retrieve the root @context object of the ''json''.
    */
  def contextValue: Json = json.hcursor.get[Json]("@context").getOrElse(Json.obj())

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
    * Attempt to fetch the [[Namespace]] for a given ''prefix''.
    * It will look up into the JSON-LD @context object whether the ''prefix'' key exists, and it will return the value associated to it.
    *
    * @param prefix the prefix to look up into the @context object
    */
  def namespaceOf(prefix: Prefix): Option[Namespace]

  /**
    * Attempt to fetch the [[Prefix]] for a given ''namespace''.
    * It will look up into the JSON-LD @context object whether the ''namespace'' value exists, and it will return the key associated to it.
    *
    * @param namespace the namespace (right side of a PrefixMapping) to look up into the @context object
    */
  def prefixOf(namespace: Namespace): Option[Prefix]

  /**
    * Attempt to expand the ''value'' using the prefix mappings available if possible.
    * Example: given a value nxv:rev and a prefix mappings available for the prefix nxv (with a value of namespace), return namespace+rev.
    * Otherwise return None.
    *
    * @param value the value to expand.
    */
  def expand(value: String): Option[Id]
}

object JsonLD {

  implicit final def apply(json: Json): JsonLD = JenaJsonLD(json)

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
    * An id which is a [[Id]] (uri)
    *
    * @param value the uri Id
    */
  final case class IdTypeUri(value: Id) extends IdType

  /**
    * An id which is a Blank Node (does not have a uri)
    *
    * @param value the blank node auto-generated string
    */
  final case class IdTypeBlank(value: String) extends IdType

}

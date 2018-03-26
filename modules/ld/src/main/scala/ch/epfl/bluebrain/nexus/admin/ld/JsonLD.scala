package ch.epfl.bluebrain.nexus.admin.ld

import ch.epfl.bluebrain.nexus.admin.ld.JsonLD.GraphUpdate.IdOrLiteral
import ch.epfl.bluebrain.nexus.admin.ld.JsonLD._
import ch.epfl.bluebrain.nexus.admin.ld.jena.JenaJsonLD
import ch.epfl.bluebrain.nexus.admin.refined.ld._
import io.circe.Json
import shapeless.ops.coproduct.Inject
import shapeless.{:+:, CNil, Typeable}

/**
  * A data type representing possible JSON-LD values.
  */
trait JsonLD extends GraphTraversal with Keywords with GraphUpdate {

  /**
    * The underlying [[Json]] data type
    */
  def json: Json

  /**
    * Creates a new [[JsonLD]] with the original context plus
    *
    * @param ctx the jsonLD containing an @context object. If some of the prefixMappings inside the @context object
    *            are present in both the existing @context and the passed one, the passed one will override the existing
    */
  def appendContext(ctx: JsonLD): JsonLD =
    this deepMerge Json.obj("@context" -> (contextValue deepMerge contextValue(ctx.json)))

  /**
    * Retrieve the root @context object of the ''json''.
    */
  def contextValue: Json = contextValue(json)

  /**
    * Perform a deep merge of this JSON value with another JSON value.
    *
    * Objects are merged by key, values from the argument JSON take
    * precedence over values from this JSON. Nested objects are
    * recursed.
    */
  def deepMerge(otherLD: JsonLD): JsonLD = json deepMerge otherLD.json

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

  private def contextValue(value: Json): Json = value.hcursor.get[Json]("@context").getOrElse(Json.obj())

}

object JsonLD {
  final def apply(json: Json): JsonLD                     = JenaJsonLD(json)
  implicit final def fromJsonInstance(json: Json): JsonLD = apply(json)
  implicit final def toJsonInstance(value: JsonLD): Json  = value.json

  private[ld] trait GraphUpdate {

    /**
      * Constructs a ''GraphUpdateBuilder'' that stores the triples to be added to the JSON-LD Graph.
      * The subject is taken from the ''GraphCursor'' pointer.
      *
      * @param p the predicate to be added
      * @param o the object to be added
      */
    def add[A](p: Id, o: A)(implicit inject: Inject[IdOrLiteral, A]): GraphUpdateBuilder

  }

  private[ld] object GraphUpdate {
    type IdOrLiteral = Id :+: IdRef :+: String :+: Boolean :+: Int :+: Long :+: Double :+: CNil
  }

  private[ld] trait GraphUpdateBuilder extends GraphUpdate {
    def apply(): Option[JsonLD]
  }

  final private[ld] case class EmptyEffectBuilder(private val rootJsonLD: JsonLD) extends GraphUpdateBuilder {

    override def add[A](p: Id, o: A)(implicit inject: Inject[IdOrLiteral, A]): GraphUpdateBuilder = this

    override def apply(): Option[JsonLD] = Some(rootJsonLD)
  }

  private[ld] trait GraphTraversal {

    /**
      * Attempt to fetch the object from the given predicate and the current cursor's subject.
      *
      * @param predicate the given predicate
      * @tparam T the generic type of the desired object
      */
    def value[T: Typeable](predicate: Id): Option[T]

    /**
      * Navigates down the graph to the objects available from the given predicate.
      *
      * @param predicate the given predicate
      * @return the list of [[GraphCursor]] available down the predicate
      */
    def down(predicate: Id): List[GraphCursor]

    /**
      * Navigates down the graph to the first available object from the given predicate.
      *
      * @param predicate the given predicate
      * @return the first object down the predicate
      */
    def downFirst(predicate: Id): GraphCursor

  }

  private[ld] trait Keywords {

    /**
      * The available root ''subject'' of the JSON-LD
      */
    def id: IdType

    /**
      * The available root ''rdf:type''s of the JSON-LD
      */
    def tpe: Set[IdRef]
  }

  private[ld] trait GraphCursor extends GraphTraversal with GraphUpdate with Keywords {

    /**
      * @return the top jsonLD object of this [[GraphCursor]]
      */
    def rootJsonLD: JsonLD
  }

  final private[ld] case class EmptyCursor(rootJsonLD: JsonLD) extends GraphCursor {

    override def id: IdType = Empty

    override def tpe: Set[IdRef] = Set.empty

    override def value[T: Typeable](predicate: Id): Option[T] = None

    override def down(predicate: Id): List[GraphCursor] = List.empty

    override def downFirst(predicate: Id): GraphCursor = this

    override def add[A](p: Id, o: A)(implicit inject: Inject[IdOrLiteral, A]): GraphUpdateBuilder =
      EmptyEffectBuilder(rootJsonLD)

  }

}

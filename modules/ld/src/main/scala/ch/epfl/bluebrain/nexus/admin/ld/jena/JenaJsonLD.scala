package ch.epfl.bluebrain.nexus.admin.ld.jena

import java.io.ByteArrayInputStream

import ch.epfl.bluebrain.nexus.admin.ld.Const._
import ch.epfl.bluebrain.nexus.admin.ld.JsonLD.GraphCursor
import ch.epfl.bluebrain.nexus.admin.ld.JsonLD.GraphUpdate.IdOrLiteral
import ch.epfl.bluebrain.nexus.admin.ld._
import ch.epfl.bluebrain.nexus.admin.refined.ld._
import eu.timepit.refined.api.RefType.applyRef
import io.circe.Json
import org.apache.jena.graph._
import org.apache.jena.rdf.model.{Model, ModelFactory}
import org.apache.jena.riot.{Lang, RDFDataMgr}
import shapeless.Typeable
import shapeless.ops.coproduct.Inject

import scala.collection.JavaConverters._

/**
  * [[JsonLD]] implementation backed by Jena
  *
  * @param json the underlying [[Json]] data type
  */
private[ld] final case class JenaJsonLD(json: Json, private[jena] val modelF: () => Model) extends JsonLD {

  private[jena] lazy val model = modelF()

  private[jena] lazy val graph = model.getGraph

  override lazy val id: IdType = {
    val (subs, objs) = graph
      .find(Node.ANY, Node.ANY, Node.ANY)
      .asScala
      .withFilter(triple => triple.getSubject.isBlankOrUri)
      .foldLeft(Set.empty[Node] -> Set.empty[Node]) {
        case ((s, o), c) if selfPredicate(c.getPredicate) => s                  -> o
        case ((s, o), c) if c.getObject.isBlankOrUri      => (s + c.getSubject) -> (o + c.getObject)
        case ((s, o), c)                                  => (s + c.getSubject) -> (o)
      }
    (subs -- objs).toList match {
      case head :: Nil => head.toIdType
      case _           => Empty
    }
  }

  private val cursor = JenaGraphCursor(this, () => id)

  override def tpe: Set[IdRef] = cursor.tpe

  override def value[T: Typeable](predicate: Id): Option[T] = cursor.value(predicate)

  override def down(predicate: Id): List[GraphCursor] = cursor.down(predicate)

  override def namespaceOf(prefix: Prefix): Option[Namespace] =
    Option(graph.getPrefixMapping.getNsPrefixURI(prefix.value)).flatMap(uri => applyRef[Namespace](uri).toOption)

  override def prefixOf(namespace: Namespace): Option[Prefix] =
    Option(graph.getPrefixMapping.getNsURIPrefix(namespace.value)).flatMap(str => applyRef[Prefix](str).toOption)

  override def expand(value: String): Option[Id] = applyRef[Id](model.expandPrefix(value)).toOption

  override def downFirst(predicate: Id): GraphCursor = cursor.downFirst(predicate)

  override def add[A](p: Id, o: A)(implicit inject: Inject[IdOrLiteral, A]): JsonLD.GraphUpdateBuilder =
    cursor.add(p, o)

  private def selfPredicate(node: Node): Boolean =
    node.toId(graph).map(_ == nxv.self).getOrElse(false)
}

private[ld] object JenaJsonLD {

  private def genModel(json: Json): Model = {
    val m = ModelFactory.createDefaultModel()
    RDFDataMgr.read(m, new ByteArrayInputStream(json.noSpaces.getBytes), Lang.JSONLD)
    m
  }

  final def apply(json: Json): JsonLD = new JenaJsonLD(json, () => genModel(json))
}

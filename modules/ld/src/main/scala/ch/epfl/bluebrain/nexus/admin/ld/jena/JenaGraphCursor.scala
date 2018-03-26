package ch.epfl.bluebrain.nexus.admin.ld.jena

import akka.http.scaladsl.model.Uri
import ch.epfl.bluebrain.nexus.admin.ld.Const.rdf
import ch.epfl.bluebrain.nexus.admin.ld.JsonLD.GraphUpdate.IdOrLiteral
import ch.epfl.bluebrain.nexus.admin.ld.JsonLD.{EmptyCursor, GraphCursor, GraphUpdateBuilder}
import ch.epfl.bluebrain.nexus.admin.ld.{Empty, IdRef, IdType, JsonLD}
import ch.epfl.bluebrain.nexus.admin.refined.ld.Id
import org.apache.jena.graph.{Graph, Node, Node_Literal, Node_URI}
import shapeless.Typeable
import shapeless.ops.coproduct.Inject

import scala.collection.JavaConverters._

/**
  * [[GraphCursor]] implementation backed by Jena
  *
  * @param rootJsonLD the top jsonLD object of this [[GraphCursor]]
  * @param idF        a function which returns the [[IdType]] of the cursor
  */
private[ld] final case class JenaGraphCursor(rootJsonLD: JenaJsonLD, idF: () => IdType) extends GraphCursor {

  override lazy val id: IdType                = idF()
  override lazy val tpe: Set[IdRef]           = tpe(id)
  private lazy val graph: Graph               = rootJsonLD.graph
  private val graphUpdate: GraphUpdateBuilder = JenaGraphUpdateBuilder(rootJsonLD, idF)

  private[jena] def tpe(idType: IdType): Set[IdRef] =
    idType.optNode match {
      case Some(node) =>
        graph.find(node, rdf.tpe.node, Node.ANY).asScala.flatMap(_.getObject.toId(graph).toOption).toSet
      case None =>
        Set.empty[IdRef]
    }

  override def value[T](predicate: Id)(implicit T: Typeable[T]): Option[T] =
    id.optNode.flatMap { node =>
      graph
        .find(node, predicate.node, Node.ANY)
        .asScala
        .map(_.getObject)
        .find(node => node.isLiteral || node.isURI)
        .flatMap {
          case node: Node_URI     => T.cast(node.getURI) orElse T.cast(Uri(node.getURI))
          case node: Node_Literal => T.cast(node.getLiteral.getValue)
          // $COVERAGE-OFF$
          case _ => None
          // $COVERAGE-ON$
        }
    }

  override def down(predicate: Id): List[GraphCursor] =
    id.optNode
      .map { node =>
        graph.find(node, predicate.node, Node.ANY).asScala.map(_.getObject).foldLeft(List.empty[GraphCursor]) {
          (acc, node) =>
            node.toIdType match {
              case Empty  => acc
              case downId => JenaGraphCursor(rootJsonLD, () => downId) :: acc
            }
        }
      }
      .getOrElse(List.empty)

  override def downFirst(predicate: Id): GraphCursor = down(predicate) match {
    case head :: _ => head
    case _         => EmptyCursor(rootJsonLD)
  }

  override def add[A](p: Id, o: A)(implicit inject: Inject[IdOrLiteral, A]): JsonLD.GraphUpdateBuilder =
    graphUpdate.add(p, o)
}

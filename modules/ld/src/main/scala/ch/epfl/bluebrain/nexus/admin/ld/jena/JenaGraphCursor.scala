package ch.epfl.bluebrain.nexus.admin.ld.jena

import akka.http.scaladsl.model.Uri
import ch.epfl.bluebrain.nexus.admin.ld.Const.rdf
import ch.epfl.bluebrain.nexus.admin.ld.JsonLD.GraphCursor
import ch.epfl.bluebrain.nexus.admin.ld.jena.JenaSyntaxes._
import ch.epfl.bluebrain.nexus.admin.ld.{Empty, IdRef, IdType}
import ch.epfl.bluebrain.nexus.admin.refined.ld.Id
import org.apache.jena.graph.{Graph, Node, Node_Literal, Node_URI}
import shapeless.Typeable

import scala.collection.JavaConverters._

/**
  * [[GraphCursor]] implementation backed by Jena
  *
  * @param idF    a function which returns the [[IdType]] of the cursor
  * @param graphF a function which returns the jsonLD [[Graph]] representation
  */
private[ld] final case class JenaGraphCursor(idF: () => IdType, private val graphF: () => Graph) extends GraphCursor {

  override lazy val id: IdType         = idF()
  override lazy val tpe: Option[IdRef] = tpe(id)
  lazy val graph: Graph                = graphF()

  private[jena] def tpe(idType: IdType) =
    for {
      node <- idType.optNode
      obj <- graph.find(node, rdf.tpe.node, Node.ANY).asScala.collectFirst {
        case t if t.getObject.isURI => t.getObject
      }
      idRef <- obj.toId(graph).toOption
    } yield idRef

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
              case downId => JenaGraphCursor(() => downId, graphF) :: acc
            }
        }
      }
      .getOrElse(List.empty)
}

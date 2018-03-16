package ch.epfl.bluebrain.nexus.admin.ld.jena

import ch.epfl.bluebrain.nexus.admin.ld.IdOps._
import ch.epfl.bluebrain.nexus.admin.ld._
import ch.epfl.bluebrain.nexus.admin.refined.ld.Id
import eu.timepit.refined.api.RefType.applyRef
import org.apache.jena.graph._

private[jena] object JenaSyntaxes {

  /**
    * Syntax sugar to expose methods on type [[Node]]
    *
    * @param node the node instance
    */
  implicit class NodeSyntax(node: Node) {
    def isBlankOrUri: Boolean = node.isBlank || node.isURI

    /**
      * Attempt to construct the [[IdRef]] from a ''node''
      *
      * @param graph the JSON LD Graph representation
      */
    def toId(graph: Graph): Either[String, IdRef] =
      if (node.isURI) {
        val uri   = node.getURI
        val short = graph.getPrefixMapping.shortForm(uri)
        if (short == uri) applyRef[Id](uri).map(_.toId)
        else
          short.split(":", 2).toList match {
            case prefix :: reference :: Nil =>
              IdRef.build(prefix, uri.replaceFirst(reference, ""), reference)
            case _ =>
              applyRef[Id](uri).map(_.toId)
          }
      } else Left("The current node is not a URI")

    /**
      * Constructs a [[IdType]] from the ''node''
      */
    def toIdType: IdType = node match {
      case n: Node_URI   => applyRef[Id](n.getURI).map(IdTypeUri).getOrElse(Invalid(n.toString()))
      case n: Node_Blank => IdTypeBlank(n.getBlankNodeId.getLabelString)
      case _             => Empty
    }
  }

  /**
    * Syntax sugar to expose methods on type [[Node]]
    *
    * @param idRef the idRef instance
    */
  implicit class IdRefNodeSyntax(idRef: IdRef) {

    /**
      * Constructs a [[Node]] from the ''idRef''
      */
    def node: Node = idRef.id.node
  }

  /**
    * Syntax sugar to expose methods on type [[Id]]
    *
    * @param id the id instance
    */
  implicit class IdNodeSyntax(id: Id) {

    /**
      * Constructs a [[Node]] from the ''id''
      */
    def node: Node = NodeFactory.createURI(id.value)
  }

  /**
    * Syntax sugar to expose methods on type [[IdType]]
    *
    * @param idType the idType instance
    */
  implicit class IdTypeNodeSyntax(idType: IdType) {

    /**
      * Attempt to construct the ''idType'' from a [[Node]]
      */
    def optNode: Option[Node] = idType match {
      case IdTypeUri(uri)     => Some(NodeFactory.createURI(uri.value))
      case IdTypeBlank(value) => Some(NodeFactory.createBlankNode(value))
      case _                  => None

    }
  }

}

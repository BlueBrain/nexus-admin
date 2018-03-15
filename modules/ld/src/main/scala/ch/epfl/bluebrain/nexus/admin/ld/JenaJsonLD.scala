package ch.epfl.bluebrain.nexus.admin.ld

import java.io.ByteArrayInputStream

import akka.http.scaladsl.model.Uri
import ch.epfl.bluebrain.nexus.admin.ld.Const._
import ch.epfl.bluebrain.nexus.admin.ld.IdOps._
import ch.epfl.bluebrain.nexus.admin.ld.JsonLD.{IdType, IdTypeBlank, IdTypeUri}
import ch.epfl.bluebrain.nexus.admin.refined.ld._
import eu.timepit.refined.api.RefType.applyRef
import io.circe.Json
import org.apache.jena.graph._
import org.apache.jena.rdf.model.ModelFactory
import org.apache.jena.riot.{Lang, RDFDataMgr}
import shapeless.Typeable

import scala.collection.JavaConverters._

/**
  * [[JsonLD]] implementation backed by Jena
  *
  * @param json the underlying [[Json]] data type
  */
private[ld] final case class JenaJsonLD(json: Json) extends JsonLD {

  private lazy val model = {
    val m = ModelFactory.createDefaultModel()
    RDFDataMgr.read(m, new ByteArrayInputStream(json.noSpaces.getBytes), Lang.JSONLD)
    m
  }

  private lazy val graph = model.getGraph

  private lazy val idNode: Option[Node] = {
    val (subs, objs) = graph
      .find(Node.ANY, Node.ANY, Node.ANY)
      .asScala
      .filter(triple => triple.getSubject.isBlankOrUri && triple.getObject.isBlankOrUri)
      .foldLeft(Set.empty[Node] -> Set.empty[Node]) {
        case ((s, o), c) if isSelfPredicate(c.getPredicate) => s                  -> o
        case ((s, o), c)                                    => (s + c.getSubject) -> (o + c.getObject)
      }
    (subs -- objs).toList match {
      case head :: Nil => Some(head)
      case _           => None
    }
  }

  private def isSelfPredicate(node: Node): Boolean =
    node.toId.map(_ == nxv.self).getOrElse(false)

  override lazy val id: Option[IdType] = idNode.flatMap {
    case node: Node_URI   => applyRef[Id](node.getURI).map(IdTypeUri).toOption
    case node: Node_Blank => Some(IdTypeBlank(node.getBlankNodeId.getLabelString))
    case _                => None
  }

  override lazy val tpe: Option[IdRef] =
    for {
      node  <- idNode
      obj   <- graph.find(node, rdf.tpe, Node.ANY).asScala.collectFirst { case t if t.getObject.isURI => t.getObject }
      idRef <- obj.toId.toOption
    } yield idRef

  override def deepMerge(other: Json): JsonLD = JenaJsonLD(json deepMerge other)

  override def predicate[T](uri: Id)(implicit T: Typeable[T]): Option[T] = {
    val p = NodeFactory.createURI(uri.value)
    graph.find(Node.ANY, p, Node.ANY).asScala.map(_.getObject).find(node => node.isLiteral || node.isURI).flatMap {
      case node: Node_URI     => T.cast(node.getURI) orElse T.cast(Uri(node.getURI))
      case node: Node_Literal => T.cast(node.getLiteral.getValue)
      // $COVERAGE-OFF$
      case _ => None
      // $COVERAGE-ON$
    }
  }

  override def prefixValueOf(prefixName: Prefix): Option[Namespace] =
    Option(graph.getPrefixMapping.getNsPrefixURI(prefixName.value)).flatMap(uri => applyRef[Namespace](uri).toOption)

  override def prefixNameOf(prefixValue: Namespace): Option[Prefix] =
    Option(graph.getPrefixMapping.getNsURIPrefix(prefixValue.value)).flatMap(str => applyRef[Prefix](str).toOption)

  private implicit def idRefToNode(idRef: IdRef): Node = NodeFactory.createURI(idRef.id.toString())

  private implicit class NodeSyntax(node: Node) {
    def isBlankOrUri: Boolean = node.isBlank || node.isURI

    def toId: Either[String, IdRef] =
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
  }

  override def expand(value: String): Option[Id] =
    applyRef[Id](model.expandPrefix(value)).toOption
}

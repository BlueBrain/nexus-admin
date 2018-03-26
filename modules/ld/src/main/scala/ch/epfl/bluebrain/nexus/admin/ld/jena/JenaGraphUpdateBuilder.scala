package ch.epfl.bluebrain.nexus.admin.ld.jena

import java.io.StringWriter

import ch.epfl.bluebrain.nexus.admin.ld.JsonLD.GraphUpdate.IdOrLiteral
import ch.epfl.bluebrain.nexus.admin.ld.JsonLD.GraphUpdateBuilder
import ch.epfl.bluebrain.nexus.admin.ld.jena.JenaGraphUpdateBuilder._
import ch.epfl.bluebrain.nexus.admin.ld.{IdRef, IdType, JsonLD}
import ch.epfl.bluebrain.nexus.admin.refined.ld.Id
import io.circe.parser.parse
import org.apache.jena.datatypes.xsd.XSDDatatype
import org.apache.jena.graph.{Graph, NodeFactory, Triple => JenaTriple}
import org.apache.jena.riot.{RDFDataMgr, RDFFormat}
import shapeless.Poly1
import shapeless.ops.coproduct.Inject

import scala.util.{Success, Try}

/**
  * A [[GraphUpdateBuilder]] implementation backed by Jena
  *
  * @param triples    the triples to be added to the graph
  * @param rootJsonLD the top jsonLD object of this [[GraphUpdateBuilder]]
  * @param idF        a function which returns the [[IdType]] of the graph builder
  */
private[ld] final case class JenaGraphUpdateBuilder(private val triples: Set[Triple],
                                                    private val rootJsonLD: JenaJsonLD,
                                                    private val idF: () => IdType)
    extends GraphUpdateBuilder {

  private lazy val id: IdType   = idF()
  private lazy val graph: Graph = rootJsonLD.graph

  override def add[A](p: Id, o: A)(implicit inject: Inject[IdOrLiteral, A]): GraphUpdateBuilder = {
    val triple = Triple(id, p, inject(o))
    if (triples.contains(triple)) this
    else JenaGraphUpdateBuilder(triples + triple, rootJsonLD, idF)
  }

  private object toNode extends Poly1 {
    implicit def caseId      = at[Id](_.node)
    implicit def caseIdRef   = at[IdRef](_.node)
    implicit def caseString  = at[String](NodeFactory.createLiteral)
    implicit def caseBoolean = at[Boolean](b => NodeFactory.createLiteral(b.toString, XSDDatatype.XSDboolean))
    implicit def caseInt     = at[Int](b => NodeFactory.createLiteral(b.toString, XSDDatatype.XSDinteger))
    implicit def caseLong    = at[Long](b => NodeFactory.createLiteral(b.toString, XSDDatatype.XSDinteger))
    implicit def caseDouble  = at[Double](b => NodeFactory.createLiteral(b.toString, XSDDatatype.XSDdouble))
  }
  override def apply(): Option[JsonLD] = {
    val applied = triples.foldLeft(false) {
      case (result, Triple(s, p, o)) =>
        s.optNode match {
          case None =>
            result
          case Some(sNode) =>
            graph.add(new JenaTriple(sNode, p.node, o.fold(toNode)))
            true
        }
    }
    if (applied) toJsonLD
    else Some(rootJsonLD)
  }

  private def toJsonLD: Option[JsonLD] = {
    val str = new StringWriter()
    Try {
      RDFDataMgr.write(str, rootJsonLD.model, RDFFormat.JSONLD_COMPACT_FLAT)
      parse(str.toString).toTry
    } match {
      case Success(Success(json)) =>
        val value = JenaJsonLD(json, () => rootJsonLD.model)
        Some(value)
      case _ =>
        Try(str.close())
        None
    }
  }
}

private[jena] object JenaGraphUpdateBuilder {

  final def apply(rootJsonLD: JenaJsonLD, idF: () => IdType): JenaGraphUpdateBuilder =
    apply(Set.empty, rootJsonLD, idF)

  final def apply(triples: Set[Triple], rootJsonLD: JenaJsonLD, idF: () => IdType): JenaGraphUpdateBuilder =
    new JenaGraphUpdateBuilder(triples, rootJsonLD, idF)

  final case class Triple(s: IdType, p: Id, o: IdOrLiteral)
}

package ch.epfl.bluebrain.nexus.admin.query.builder

import ch.epfl.bluebrain.nexus.admin.query.filtering.Expr
import ch.epfl.bluebrain.nexus.admin.query.filtering.Expr.ComparisonExpr
import ch.epfl.bluebrain.nexus.admin.query.filtering.Op.Eq
import ch.epfl.bluebrain.nexus.admin.query.filtering.PropPath.UriPath
import ch.epfl.bluebrain.nexus.admin.query.filtering.Term.UriTerm
import ch.epfl.bluebrain.nexus.admin.refined.project.ProjectReference
import ch.epfl.bluebrain.nexus.admin.ld.Const._

/**
  * Defines the creation of a type expression to target specific type resources.
  *
  * @tparam Id the generic type which defines the targeted resource
  */
trait TypeFilterExpr[Id] {

  /**
    * Creates an expression to target type resources for ''Id''
    */
  def apply(): Expr
}

object TypeFilterExpr {

  implicit val projFilterExpr = new TypeFilterExpr[ProjectReference] {
    override def apply() =
      ComparisonExpr(Eq, UriPath(rdf.tpe.value), UriTerm(nxv.Project.value))
  }

}

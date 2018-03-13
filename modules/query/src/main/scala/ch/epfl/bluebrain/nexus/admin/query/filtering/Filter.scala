package ch.epfl.bluebrain.nexus.admin.query.filtering

import ch.epfl.bluebrain.nexus.admin.query.filtering.Expr.NoopExpr

/**
  * Filter representation that wraps a single filtering expression.
  *
  * @param expr the expression to be evaluated
  */
final case class Filter(expr: Expr)

object Filter {

  val Empty = Filter(NoopExpr)
}

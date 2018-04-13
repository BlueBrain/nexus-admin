package ch.epfl.bluebrain.nexus.admin.query.builder

import java.util.concurrent.atomic.AtomicInteger

import akka.http.scaladsl.model.Uri
import cats.Show
import cats.syntax.show._
import ch.epfl.bluebrain.nexus.admin.ld.Const.SelectTerms._
import ch.epfl.bluebrain.nexus.admin.ld.Const.{bds, nxv}
import ch.epfl.bluebrain.nexus.admin.ld.IdResolvable
import ch.epfl.bluebrain.nexus.admin.query.QueryPayload
import ch.epfl.bluebrain.nexus.admin.query.filtering.Expr.{ComparisonExpr, InExpr, LogicalExpr, NoopExpr}
import ch.epfl.bluebrain.nexus.admin.query.filtering.Op._
import ch.epfl.bluebrain.nexus.admin.query.filtering.PropPath.{SubjectPath, UriPath}
import ch.epfl.bluebrain.nexus.admin.query.filtering.Term.{LiteralTerm, TermCollection, UriTerm}
import ch.epfl.bluebrain.nexus.admin.query.filtering._
import ch.epfl.bluebrain.nexus.admin.refined.permissions.HasReadProjects
import ch.epfl.bluebrain.nexus.admin.refined.project.ProjectReference
import ch.epfl.bluebrain.nexus.commons.types.search.Sort.OrderType.Desc
import ch.epfl.bluebrain.nexus.commons.types.search.{Pagination, Sort, SortList}

/**
  * Describes paginated queries based on filters.
  */
object FilteredQuery {

  /**
    * Constructs a Blazegraph query based on the provided filter and pagination settings that also computes the total
    * number of results.  The filter is applied on the subjects.
    *
    * @param query      the query payload used to build the Sparql query
    * @param pagination the pagination settings for the generated query
    * @return a Blazegraph query based on the provided filter and pagination settings
    */
  def apply[Id](query: QueryPayload, pagination: Pagination, acls: HasReadProjects)(
      implicit typeExpr: TypeFilterExpr[Id],
      idRes: IdResolvable[ProjectReference]): String = {
    applyWithWhere(
      buildWhereFrom(
        addToFilter(query.filter and ResourceRestrictionExpr(acls.value), query.deprecated, query.published).expr),
      pagination,
      query.q,
      query.sort)
  }

  private[query] def deprecated(deprecatedOps: Option[Boolean]): Filter =
    Filter(fieldOrNoop(nxv.deprecated.value, deprecatedOps))

  private[query] def published(publishedOps: Option[Boolean]): Filter =
    Filter(fieldOrNoop(nxv.published.value, publishedOps))

  private def fieldOrNoop(name: Uri, field: Option[Boolean]): Expr =
    field
      .map { value =>
        ComparisonExpr(Op.Eq, UriPath(name), LiteralTerm(value.toString))
      }
      .getOrElse(NoopExpr)

  private def addToFilter[Id](filter: Filter, depOpt: Option[Boolean], pubOpt: Option[Boolean])(
      implicit
      typeExpr: TypeFilterExpr[Id]) =
    Filter(typeExpr.apply) and filter.expr and deprecated(depOpt).expr and published(pubOpt).expr

  private def applyWithWhere[Id](where: String,
                                 pagination: Pagination,
                                 term: Option[String],
                                 sort: SortList): String = {
    val (selectTotal, selectWith, selectSubQuery) = buildSelectsFrom(term, sort)
    val (orderByUnion, orderByTotal)              = buildOrderByFrom(term, sort)

    s"""
       |PREFIX bds: <${bds.namespaceBuilder.value}>
       |$selectTotal
       |WITH {
       |  $selectWith
       |  WHERE {
       |${buildWhereFrom(term)}
       |$where
       |${sort.toTriples}
       |  }
       |${buildGroupByFrom(term)}
       |} AS %resultSet
       |WHERE {
       |  {
       |    $selectSubQuery
       |    WHERE { INCLUDE %resultSet }
       |  }
       |  UNION
       |  {
       |    SELECT *
       |    WHERE { INCLUDE %resultSet }
       |    $orderByUnion
       |    LIMIT ${pagination.size}
       |    OFFSET ${pagination.from}
       |  }
       |}
       |$orderByTotal""".stripMargin
  }

  /**
    * Constructs a Blazegraph query based on the provided filters and pagination settings that also computes the total
    * number of results.
    *
    * @param query       the query payload used to build the Sparql query
    * @param thisSubject the qualified uri of the subject to be selected
    * @param pagination  the pagination settings for the generated query
    */
  def outgoing[Id](query: QueryPayload, thisSubject: Uri, pagination: Pagination)(
      implicit
      typeExpr: TypeFilterExpr[Id]): String = {
    val filter = addToFilter(query.filter, query.deprecated, query.published).expr
    val where =
      s"""
         |<$thisSubject> ?p ?$subject .
         |${buildWhereFrom(filter)}
       """.stripMargin.trim
    applyWithWhere(where, pagination, query.q, query.sort)
  }

  /**
    * Constructs a Blazegraph query based on the provided filters and pagination settings that also computes the total
    * number of results.
    *
    * @param query      the query payload used to build the Sparql query
    * @param thisObject the qualified uri of the object to be selected
    * @param pagination the pagination settings for the generated query
    */
  def incoming[Id](query: QueryPayload, thisObject: Uri, pagination: Pagination)(
      implicit
      typeExpr: TypeFilterExpr[Id]): String = {
    val filter = addToFilter(query.filter, query.deprecated, query.published).expr

    val where =
      s"""
         |?$subject ?p <$thisObject> .
         |${buildWhereFrom(filter)}
       """.stripMargin.trim
    applyWithWhere(where, pagination, query.q, query.sort)
  }

  private final case class Stmt(stmt: Option[String], filter: Option[String])

  private def buildSelectsFrom(term: Option[String], sort: SortList): (String, String, String) = {
    term
      .map(
        _ =>
          (
            s"SELECT DISTINCT ?$total ?$subject ?$maxScore ?$score ?$rank",
            s"SELECT DISTINCT ?$subject ${sort.toVars} (max(?rsv) AS ?$score) (max(?pos) AS ?$rank)",
            s"SELECT (COUNT(DISTINCT ?$subject) AS ?$total) (max(?$score) AS ?$maxScore)"
        ))
      .getOrElse(
        (
          s"SELECT DISTINCT ?$total ?$subject",
          s"SELECT DISTINCT ?$subject ${sort.toVars}".trim,
          s"SELECT (COUNT(DISTINCT ?$subject) AS ?$total)"
        )
      )
  }

  private def buildOrderByFrom(term: Option[String], sort: SortList): (String, String) =
    term
      .map(_ => (s"ORDER BY ${sort.toOrderByClause}", s"ORDER BY DESC(?$score)"))
      .getOrElse((s"ORDER BY ${sort.toOrderByClause}", ""))

  private def buildGroupByFrom(term: Option[String]): String =
    term.map(_ => s"GROUP BY ?$subject").getOrElse("")

  private def buildWhereFrom(term: Option[String]): String =
    term.map(term => s"""
                        |?$subject ?matchedProperty ?matchedValue .
                        |?matchedValue ${bds.search.curie.show} "$term" .
                        |?matchedValue ${bds.relevance.curie.show} ?rsv .
                        |?matchedValue ${bds.rank.curie.show} ?pos .
                        |FILTER ( !isBlank(?s) )
     """.stripMargin.trim).getOrElse("")

  private def buildWhereFrom(expr: Expr): String = {
    val varPrefix = "var"
    val atomicIdx = new AtomicInteger(0)

    def nextIdx(): Int =
      atomicIdx.incrementAndGet()

    // ?s :p ?var
    // FILTER (?var IN (term1, term2))
    def inExpr(expr: InExpr): Stmt = {
      val idx                                 = nextIdx()
      val variable                            = s"?${varPrefix}_$idx"
      val InExpr(path, TermCollection(terms)) = expr
      val stmt                                = s"?$subject ${path.show} $variable ."
      val filter                              = terms.map(_.show).mkString(s"$variable IN (", ", ", ")")
      Stmt(Some(stmt), Some(filter))
    }

    // ?s :p ?var .
    // FILTER ( ?var op term )
    def compExpr(expr: ComparisonExpr, allowDirectFilter: Boolean = false): Stmt = {
      expr match {
        case ComparisonExpr(op, SubjectPath, term) =>
          Stmt(None, Some(s"${(SubjectPath: PropPath).show} ${op.show} ${term.show}"))

        case ComparisonExpr(_: Eq.type, path, term: UriTerm) if allowDirectFilter =>
          Stmt(Some(s"?$subject ${path.show} ${term.show} ."), None)
        case ComparisonExpr(op, path, term) =>
          val idx      = nextIdx()
          val variable = s"?${varPrefix}_$idx"
          Stmt(Some(s"?$subject ${path.show} $variable ."), Some(s"$variable ${op.show} ${term.show}"))
      }
    }

    def fromStmts(op: LogicalOp, stmts: Vector[Stmt]): String = op match {
      case And =>
        val select = stmts.collect { case Stmt(Some(stmt), _) => stmt }.mkString("\n")
        val filter = stmts.collect { case Stmt(_, Some(f)) => f }.mkString(" && ") match {
          case ""    => None
          case other => Some(other)
        }
        s"""
           |$select
           |${filter.map(f => s"FILTER ( $f )").getOrElse("")}
           |""".stripMargin
      case Or =>
        val select = stmts.collect { case Stmt(Some(stmt), _) => s"OPTIONAL { ${stmt} }" }.mkString("\n")
        val filter = stmts.collect { case Stmt(_, Some(f))    => f }.mkString(" || ")
        s"""
           |$select
           |FILTER ( $filter )
           |""".stripMargin
      case Not =>
        val select = stmts.collect { case Stmt(Some(stmt), _) => stmt }.mkString("\n")
        val filter = stmts.collect { case Stmt(_, Some(f))    => f }.mkString(" || ")
        s"""
           |$select
           |FILTER NOT EXISTS {
           |$select
           |FILTER ( $filter )
           |}
           |""".stripMargin
      case Xor =>
        val optSelect = stmts.collect { case Stmt(Some(stmt), _) => s"OPTIONAL { ${stmt} }" }.mkString("\n")
        val filter    = stmts.collect { case Stmt(_, Some(f))    => f }.mkString(" || ")
        val select    = stmts.collect { case Stmt(Some(stmt), _) => stmt }.mkString("\n")
        s"""
           |$optSelect
           |FILTER ( $filter )
           |FILTER NOT EXISTS {
           |$select
           |FILTER ( $filter )
           |}
           |""".stripMargin

    }

    def fromExpr(expr: Expr, allowDirectFilter: Boolean): String = expr match {
      case NoopExpr          => s"?$subject ?p ?o ."
      case e: ComparisonExpr => compExpr(e, allowDirectFilter).show
      case e: InExpr         => inExpr(e).show
      case LogicalExpr(And, exprs) =>
        val (statements, builder) = exprs.foldLeft((Vector.empty[Stmt], StringBuilder.newBuilder)) {
          case ((stmts, str), e: ComparisonExpr)       => (stmts :+ compExpr(e, allowDirectFilter), str)
          case ((stmts, str), e: InExpr)               => (stmts :+ inExpr(e), str)
          case ((stmts, str), l @ LogicalExpr(And, _)) => (stmts, str.append(fromExpr(l, allowDirectFilter)))
          case ((stmts, str), l: LogicalExpr)          => (stmts, str.append(fromExpr(l, allowDirectFilter = false)))
          case ((stmts, str), NoopExpr)                => (stmts, str)
        }
        fromStmts(And, statements) + builder.mkString
      case LogicalExpr(op, exprs) =>
        val statements = exprs.foldLeft(Vector.empty[Stmt]) {
          case (acc, e: ComparisonExpr) =>
            acc :+ compExpr(e)
          case (acc, e: InExpr) =>
            acc :+ inExpr(e)
          case (acc, _) =>
            // discard nested logical expressions that are not joined by 'And'
            // TODO: need better handling of logical expr
            acc
        }
        fromStmts(op, statements)
    }

    fromExpr(expr, allowDirectFilter = true)
  }

  private implicit val uriTermShow: Show[UriTerm] =
    Show.show(v => s"<${v.value.toString()}>")

  private implicit val litTermShow: Show[LiteralTerm] =
    Show.show(v => v.lexicalForm)

  private implicit val termShow: Show[Term] =
    Show.show {
      case t: UriTerm     => uriTermShow.show(t)
      case t: LiteralTerm => litTermShow.show(t)
    }

  private implicit val comparisonOpShow: Show[ComparisonOp] =
    Show.show {
      case Eq  => "="
      case Ne  => "!="
      case Lt  => "<"
      case Lte => "<="
      case Gt  => ">"
      case Gte => ">="
    }

  private implicit val stmtShow: Show[Stmt] =
    Show.show {
      case Stmt(Some(stmt), Some(filter)) =>
        s"""
           |$stmt
           |FILTER ( $filter )
           |""".stripMargin
      case Stmt(Some(stmt), None)   => stmt
      case Stmt(None, Some(filter)) => s"FILTER ( $filter )"
      case Stmt(None, None)         => ""
    }

  /**
    * Syntactic sugar for composing filters using the [[And]] logical operator.
    */
  implicit class FilterOps(filter: Filter) {

    /**
      * Constructs a new filter based on ''this'' filter by adding the argument filter expression to the expressions
      * defined in ''this'' filter using the [[And]] logical operator.
      *
      * @param expr the expression to add to ''this'' filter.
      */
    def and(expr: Expr): Filter = {
      expr match {
        case LogicalExpr(And, exprList) =>
          filter.expr match {
            case LogicalExpr(And, exprs) => Filter(LogicalExpr(And, exprs ++ exprList))
            case other                   => Filter(LogicalExpr(And, other +: exprList))
          }
        case _ =>
          filter.expr match {
            case LogicalExpr(And, exprs) => Filter(LogicalExpr(And, expr +: exprs))
            case other                   => Filter(LogicalExpr(And, List(expr, other)))
          }
      }
    }

    /**
      * Constructs a new filter based on ''this'' filter by adding the argument filter expression (if defined) to the expressions
      * defined in ''this'' filter using the [[And]] logical operator.
      *
      * @param exprOpt the optional expression to add to ''this'' filter.
      */
    def and(exprOpt: Option[Expr]): Filter =
      exprOpt.map(and).getOrElse(filter)
  }

  private[query] implicit class StringSyntax(value: String) {
    def prefixSuffixNonEmpty[A](prefix: String, suffix: String): String =
      if (value.trim.isEmpty) value else prefix + value + suffix
  }

  /**
    * Interface syntax to expose new functionality into SortList type
    *
    * @param sort the [[SortList]] instance
    */
  implicit class SortListSyntax(sort: SortList) {
    private lazy val toVarsMapping = sort.values.zipWithIndex.map { case (sort, i) => sort -> s"?sort$i" }

    /**
      * @return the string representation of the variables used for sorting (inside the SELECT clause) in SPARQL language
      */
    def toVars = toVarsMapping.map { case (_, variable) => variable }.mkString(" ")

    /**
      * @return the string representation of the optional triples used for sorting (inside the WHERE clause) in SPARQL language
      */
    def toTriples =
      toVarsMapping
        .map {
          case (Sort(_, predicate), variable) => s"?s <${predicate}> $variable"
        }
        .mkString("}\nOPTIONAL{") prefixSuffixNonEmpty ("OPTIONAL{", "}")

    /**
      * @return the string representation of the ORDER BY clause used for sorting in SPARQL language
      */
    def toOrderByClause =
      Option(toVarsMapping.map {
        case (Sort(Desc, _), variable) => s"DESC($variable)"
        case (_, variable)             => s"ASC($variable)"
      }).filterNot(_.isEmpty).getOrElse(List("?s")).mkString(" ")

  }
}

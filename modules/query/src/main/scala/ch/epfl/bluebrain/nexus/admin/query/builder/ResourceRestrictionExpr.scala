package ch.epfl.bluebrain.nexus.admin.query.builder

import ch.epfl.bluebrain.nexus.admin.ld.Const.nxv
import ch.epfl.bluebrain.nexus.admin.query.filtering.Expr.{ComparisonExpr, LogicalExpr, NoopExpr}
import ch.epfl.bluebrain.nexus.admin.query.filtering.Op.Or
import ch.epfl.bluebrain.nexus.admin.query.filtering.PropPath.UriPath
import ch.epfl.bluebrain.nexus.admin.query.filtering.Term.LiteralTerm
import ch.epfl.bluebrain.nexus.admin.query.filtering.{Expr, Op}
import ch.epfl.bluebrain.nexus.admin.refined.project.ProjectReference
import ch.epfl.bluebrain.nexus.commons.iam.acls.{FullAccessControlList, Path, Permission, Permissions}
import eu.timepit.refined.api.RefType.applyRef
import shapeless.{:+:, CNil, Coproduct}

object ResourceRestrictionExpr {

  protected case object Root

  protected type Ids = Root.type :+: ProjectReference :+: CNil

  private implicit def toIds(path: Path): Option[Ids] = path.segments match {
    case _ :: project :: Nil => applyRef[ProjectReference](project).toOption.map(Coproduct[Ids](_))
    case _ :: Nil            => Some(Coproduct[Ids](Root))
    case _                   => None
  }

  final def apply(acls: FullAccessControlList): Expr = {
    acls.toPathMap.collect {
      case (path, perms)
          if perms.containsAny(Permissions(Permission("projects/read"), Permission("projects/manage"))) =>
        (path: Option[Ids]).map(mapToExpr)
    }.flatten match {
      case Nil                                  => NoopExpr
      case expr :: Nil                          => expr
      case exprs if exprs.exists(_ == NoopExpr) => NoopExpr
      case exprs                                => LogicalExpr(Or, exprs.toList)
    }
  }

  object idsToExpr extends shapeless.Poly1 {
    implicit val atRoot: Case.Aux[Root.type, Expr] = at(_ => NoopExpr)

    implicit val atProject: Case.Aux[ProjectReference, Expr] = at(
      pr => ComparisonExpr(Op.Eq, UriPath(nxv.name.value), LiteralTerm(pr.value)))
  }

  def mapToExpr(id: Ids): Expr = id.fold(idsToExpr)
}

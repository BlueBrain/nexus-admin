package ch.epfl.bluebrain.nexus.admin.query.builder

import ch.epfl.bluebrain.nexus.admin.ld.IdResolvable
import ch.epfl.bluebrain.nexus.admin.query.filtering.Expr.{ComparisonExpr, LogicalExpr, NoopExpr}
import ch.epfl.bluebrain.nexus.admin.query.filtering.Op.Or
import ch.epfl.bluebrain.nexus.admin.query.filtering.PropPath.SubjectPath
import ch.epfl.bluebrain.nexus.admin.query.filtering.Term.UriTerm
import ch.epfl.bluebrain.nexus.admin.query.filtering.{Expr, Op}
import ch.epfl.bluebrain.nexus.admin.refined.project.ProjectReference
import ch.epfl.bluebrain.nexus.iam.client.types._
import ch.epfl.bluebrain.nexus.service.http.Path
import eu.timepit.refined.api.RefType.applyRef
import shapeless.{:+:, CNil, Coproduct}

object ResourceRestrictionExpr {

  protected case object Root

  protected type Ids = Root.type :+: ProjectReference :+: CNil

  private implicit def toIds(path: Path): Option[Ids] = path.segments match {
    case org :: project :: Nil => applyRef[ProjectReference](s"$org/$project").toOption.map(Coproduct[Ids](_))
    case Nil                   => Some(Coproduct[Ids](Root))
    case _                     => None
  }

  final def apply(acls: FullAccessControlList)(implicit idResolvable: IdResolvable[ProjectReference]): Expr = {
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

    implicit def atProject(implicit idResolvable: IdResolvable[ProjectReference]): Case.Aux[ProjectReference, Expr] =
      at(pr => ComparisonExpr(Op.Eq, SubjectPath, UriTerm(idResolvable(pr).value)))
  }

  def mapToExpr(id: Ids)(implicit idResolvable: IdResolvable[ProjectReference]): Expr = id.fold(idsToExpr)
}

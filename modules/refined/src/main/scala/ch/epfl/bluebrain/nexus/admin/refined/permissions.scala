package ch.epfl.bluebrain.nexus.admin.refined

import ch.epfl.bluebrain.nexus.admin.refined.permissions.{ManageProjects, CreateProjects, ReadProjects, WriteProjects}
import ch.epfl.bluebrain.nexus.commons.iam.acls.{Permission, Permissions}
import eu.timepit.refined.W
import eu.timepit.refined.api.Inference.==>
import eu.timepit.refined.api.{Inference, Refined, Validate}
import shapeless.Witness
import eu.timepit.refined.boolean.Or

import scala.util.Try

@SuppressWarnings(Array("BoundedByFinalType"))
object permissions extends PermissionInferences {

  private[refined] type ManageProjects = ContainsPermission[W.`"projects/manage"`.T]
  private[refined] type ReadProjects   = ContainsPermission[W.`"projects/read"`.T] Or ManageProjects
  private[refined] type WriteProjects  = ContainsPermission[W.`"projects/write"`.T] Or ManageProjects
  private[refined] type CreateProjects = ContainsPermission[W.`"projects/create"`.T] Or ManageProjects

  /**
    * Refined type for [[Permissions]] which contain the permission ''projects/manage''
    */
  type HasManageProjects = Permissions Refined ManageProjects

  /**
    * Refined type for [[Permissions]] which contain the permission ''projects/read''
    */
  type HasReadProjects = Permissions Refined ReadProjects

  /**
    * Refined type for [[Permissions]] which contain the permission ''projects/write''
    */
  type HasWriteProjects = Permissions Refined WriteProjects

  /**
    * Refined type for [[Permissions]] which contain the permission ''projects/create''
    */
  type HasCreateProjects = Permissions Refined CreateProjects

  /** Predicate that checks if `Permissions` contains a permission `S`. */
  final private[permissions] case class ContainsPermission[S <: String](perm: S)

  object ContainsPermission {
    implicit final def validateContainsPermission[S <: String](
        implicit ws: Witness.Aux[S]): Validate.Plain[Permissions, ContainsPermission[S]] =
      Validate.fromPredicate(
        perms => Try(Permissions(Permission(ws.value))).map(p => perms.containsAny(p)).getOrElse(false),
        t => s""""$t".containsAny("${ws.value}")""",
        ContainsPermission(ws.value)
      )
  }
}

trait PermissionInferences {

  implicit val manageProjectsImpliesReadProjects: ManageProjects ==> ReadProjects =
    Inference.alwaysValid("manageProjectsImplies(readProjects)")

  implicit val manageProjectsImpliesWriteProjects: ManageProjects ==> WriteProjects =
    Inference.alwaysValid("manageProjectsImplies(writeProjects)")

  implicit val manageProjectsImpliesOwnProjects: ManageProjects ==> CreateProjects =
    Inference.alwaysValid("manageProjectsImplies(ownProjects)")

}

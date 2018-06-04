package ch.epfl.bluebrain.nexus.admin.refined

import ch.epfl.bluebrain.nexus.admin.refined.permissions.{
  CreateProjects,
  ManageOrganizatons,
  ManageProjects,
  ReadProjects,
  WriteProjects
}
import ch.epfl.bluebrain.nexus.iam.client.types._
import eu.timepit.refined.W
import eu.timepit.refined.api.Inference.==>
import eu.timepit.refined.api.{Inference, Refined, Validate}
import eu.timepit.refined.boolean.Or
import shapeless.Witness

import scala.util.Try

@SuppressWarnings(Array("BoundedByFinalType"))
object permissions extends PermissionInferences {

  private[refined] type ManageProjects = ContainsPermission[W.`"projects/manage"`.T]
  private[refined] type ReadProjects   = ContainsPermission[W.`"projects/read"`.T] Or ManageProjects
  private[refined] type WriteProjects  = ContainsPermission[W.`"projects/write"`.T] Or ManageProjects
  private[refined] type CreateProjects = ContainsPermission[W.`"projects/create"`.T] Or ManageProjects

  private[refined] type ManageOrganizatons = ContainsPermission[W.`"orgs/manage"`.T]
  private[refined] type ReadOrganizatons   = ContainsPermission[W.`"orgs/read"`.T] Or ManageOrganizatons
  private[refined] type WriteOrganizatons  = ContainsPermission[W.`"orgs/write"`.T] Or ManageOrganizatons
  private[refined] type CreateOrganizatons = ContainsPermission[W.`"orgs/create"`.T] Or ManageOrganizatons

  /**
    * Refined type for [[FullAccessControlList]] which contain the permission ''projects/manage''
    */
  type HasManageProjects = FullAccessControlList Refined ManageProjects

  /**
    * Refined type for [[FullAccessControlList]] which contain the permission ''projects/read''
    */
  type HasReadProjects = FullAccessControlList Refined ReadProjects

  /**
    * Refined type for [[FullAccessControlList]] which contain the permission ''projects/write''
    */
  type HasWriteProjects = FullAccessControlList Refined WriteProjects

  /**
    * Refined type for [[FullAccessControlList]] which contain the permission ''projects/create''
    */
  type HasCreateProjects = FullAccessControlList Refined CreateProjects

  type HasManageOrganizations = FullAccessControlList Refined ManageOrganizatons
  type HasReadOrganizations   = FullAccessControlList Refined ReadOrganizatons
  type HasWriteOrganizations  = FullAccessControlList Refined WriteOrganizatons
  type HasCreateOrganizations = FullAccessControlList Refined CreateOrganizatons

  /** Predicate that checks if `FullAccessControlList` contains a permission `S`. */
  final private[permissions] case class ContainsPermission[S <: String](perm: S)

  object ContainsPermission {
    implicit final def validateContainsPermission[S <: String](
        implicit ws: Witness.Aux[S]): Validate.Plain[FullAccessControlList, ContainsPermission[S]] =
      Validate.fromPredicate(
        perms => Try(Permissions(Permission(ws.value))).map(p => perms.permissions.containsAny(p)).getOrElse(false),
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

  implicit val manageProjectsImpliesCreateProjects: ManageProjects ==> CreateProjects =
    Inference.alwaysValid("manageProjectsImplies(createProjects)")

  implicit val manageOrgsImpliesReadOrgs: ManageOrganizatons ==> ReadProjects =
    Inference.alwaysValid("manageOrgsImplies(readOrgs")
  implicit val manageOrgsImpliesWriteOrgs: ManageOrganizatons ==> WriteProjects =
    Inference.alwaysValid("manageOrgsImplies(readOrgs)")
  implicit val manageOrgsImpliesCreateOrgs: ManageOrganizatons ==> CreateProjects =
    Inference.alwaysValid("manageOrgsImplies(createOrgs")

}

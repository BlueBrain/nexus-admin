package ch.epfl.bluebrain.nexus.admin.organizations

sealed trait OrganizationRejection extends Product with Serializable

object OrganizationRejection {

  /**
    * Signals an error while decoding an organization JSON payload.
    *
    * @param message human readable error details
    */
  final case class InvalidOrganizationFormat(message: String) extends OrganizationRejection

  /**
    * Signals the the organization already exists.
    */
  final case object OrganizationAlreadyExists extends OrganizationRejection

  /**
    * Signals that the organization does not exist.
    */
  final case object OrganizationDoesNotExist extends OrganizationRejection

  /**
    * Signals that the provided revision does not match the latest revision
    * @param latest    latest know revision
    * @param provided  provided revision
    */
  final case class IncorrectRev(latest: Long, provided: Long) extends OrganizationRejection

}

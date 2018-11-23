package ch.epfl.bluebrain.nexus.admin.organizations
import java.time.Instant

import ch.epfl.bluebrain.nexus.admin.config.AppConfig.HttpConfig
import ch.epfl.bluebrain.nexus.admin.config.Vocabulary.nxv
import ch.epfl.bluebrain.nexus.admin.types.{ResourceF, ResourceMetadata}
import ch.epfl.bluebrain.nexus.commons.types.identity.Identity

/**
  * Enumeration of organization states.
  */
sealed trait OrganizationState

object OrganizationState {

  /**
    * The initial (undefined) state.
    */
  final case object Initial extends OrganizationState

  /**
    * Initial organization state.
    *
    * @param organization
    * @param rev          the organization revision
    * @param deprecated   the deprecation status of the organization
    * @param createdAt    the instant when the organization was created
    * @param updatedAt    the instant when the organization was last updated
    * @param createdBy    the identity that created the organization
    * @param updatedBy    the identity that last updated the organization
    */
  final case class Current(organization: Organization,
                           rev: Long,
                           deprecated: Boolean,
                           createdAt: Instant,
                           updatedAt: Instant,
                           createdBy: Identity,
                           updatedBy: Identity)
      extends OrganizationState {

    def toResource(implicit http: HttpConfig): ResourceF[Organization] =
      ResourceF(http.apiIri + "orgs" + organization.label,
                rev,
                deprecated,
                Set(nxv.Organization),
                createdAt,
                createdBy,
                updatedAt,
                updatedBy,
                organization)

    def toResourceMetadata(implicit http: HttpConfig): ResourceMetadata =
      ResourceF.unit(http.apiIri + "orgs" + organization.label,
                     rev,
                     deprecated,
                     Set(nxv.Organization),
                     createdAt,
                     createdBy,
                     updatedAt,
                     updatedBy,
      )
  }
}

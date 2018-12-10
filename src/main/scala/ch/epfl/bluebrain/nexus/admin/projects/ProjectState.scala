package ch.epfl.bluebrain.nexus.admin.projects

import java.time.Instant
import java.util.UUID

import ch.epfl.bluebrain.nexus.iam.client.types.Identity.Subject


sealed trait ProjectState extends Product with Serializable

object ProjectState {

  /**
    * Initial state for all resources.
    */
  final case object Initial extends ProjectState

  /**
    * State used for all resources that have been created and later possibly updated or deprecated.
    *
    * @param id           the permanent identifier for the project
    * @param organization the permanent identifier of the parent organization
    * @param label        the label (segment) of the resource
    * @param description  an optional project description
    * @param rev          the selected revision number
    * @param instant      the timestamp associated with this state
    * @param subject      the identity associated with this state
    * @param deprecated   the deprecation status
    */
  final case class Current(id: UUID,
                           organization: UUID,
                           label: String,
                           description: Option[String],
                           rev: Long,
                           instant: Instant,
                           subject: Subject,
                           deprecated: Boolean)
      extends ProjectState

}

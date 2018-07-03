package ch.epfl.bluebrain.nexus.admin.core.resources

import ch.epfl.bluebrain.nexus.admin.core.types.Versioned
import ch.epfl.bluebrain.nexus.admin.refined.ld.Id
import ch.epfl.bluebrain.nexus.commons.types.Meta
import io.circe.Json

/**
  * Enumeration type for all events that are emitted for resources.
  */
sealed trait ResourceEvent extends Product with Serializable with Versioned {

  def id: Id
  def uuid: String
  def meta: Meta
  def tags: Set[String]
}

object ResourceEvent {

  /**
    * Evidence that a resource has been created.
    *
    * @param id         the unique identifier of the resource
    * @param label      the label (segment) of the resource
    * @param uuid       the permanent identifier for the resource
    * @param parentUuid the permanent identifier for the parent resource, if any
    * @param rev        the revision number that this event generates
    * @param meta       the metadata associated to this event
    * @param tags       the tags added to this event
    * @param value      the json payload of the resource
    */
  final case class ResourceCreated(id: Id,
                                   label: String,
                                   uuid: String,
                                   parentUuid: Option[String],
                                   rev: Long,
                                   meta: Meta,
                                   tags: Set[String],
                                   value: Json)
      extends ResourceEvent

  /**
    * Evidence that a resource has been updated.
    *
    * @param id         the unique identifier of the resource
    * @param uuid       the permanent identifier for the resource
    * @param parentUuid the permanent identifier for the parent resource, if any
    * @param rev        the revision number that this event generates
    * @param meta       the metadata associated to this event
    * @param tags       the tags added to this event
    * @param value the new json payload of the resource
    */
  final case class ResourceUpdated(id: Id,
                                   uuid: String,
                                   parentUuid: Option[String],
                                   rev: Long,
                                   meta: Meta,
                                   tags: Set[String],
                                   value: Json)
      extends ResourceEvent

  /**
    * Evidence that a resource has been deprecated.
    *
    * @param id         the unique identifier of the resource
    * @param uuid       the permanent identifier for the resource
    * @param parentUuid the permanent identifier for the parent resource, if any
    * @param rev        the revision number that this event generates
    * @param meta       the metadata associated to this event
    * @param tags       the tags added to this event
    */
  final case class ResourceDeprecated(id: Id,
                                      uuid: String,
                                      parentUuid: Option[String],
                                      rev: Long,
                                      meta: Meta,
                                      tags: Set[String])
      extends ResourceEvent

}

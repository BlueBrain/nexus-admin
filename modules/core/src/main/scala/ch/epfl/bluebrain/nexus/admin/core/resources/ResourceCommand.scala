package ch.epfl.bluebrain.nexus.admin.core.resources

import ch.epfl.bluebrain.nexus.admin.core.types.Versioned
import ch.epfl.bluebrain.nexus.admin.refined.ld.Id
import ch.epfl.bluebrain.nexus.commons.types.Meta
import io.circe.Json

/**
  * Enumeration type for commands that apply to resources.
  */
sealed trait ResourceCommand extends Product with Serializable {

  def id: Id
  def meta: Meta
  def tags: Set[String]
}

object ResourceCommand {

  /**
    * Command that signals the intent to create a new resource.
    *
    * @param id    the identifier for the resource to be created
    * @param uuid  the permanent identifier for the resource
    * @param meta  the metadata associated to this command
    * @param tags  the tags added to the consequent [[ch.epfl.bluebrain.nexus.admin.core.resources.ResourceEvent]] which might be created as a result of this operation
    * @param value the json payload of the resource
    */
  final case class CreateResource(id: Id,
                                  uuid: String,
                                  parentUuid: Option[String],
                                  meta: Meta,
                                  tags: Set[String],
                                  value: Json)
      extends ResourceCommand

  /**
    * Command that signals the intent to update a resource payload.
    *
    * @param id    the identifier for the resource to be created
    * @param rev   the last known revision of the resource
    * @param meta  the metadata associated to this command
    * @param tags  the tags added to the consequent [[ch.epfl.bluebrain.nexus.admin.core.resources.ResourceEvent]] which might be created as a result of this operation
    * @param value the json payload of the resource
    */
  final case class UpdateResource(id: Id, rev: Long, meta: Meta, tags: Set[String], value: Json)
      extends ResourceCommand
      with Versioned

  /**
    * Command that signals the intent to deprecate a resource.
    *
    * @param id    the identifier for the resource to be created
    * @param rev   the last known revision of the resource
    * @param meta  the metadata associated to this command
    * @param tags  the tags added to the consequent [[ch.epfl.bluebrain.nexus.admin.core.resources.ResourceEvent]] which might be created as a result of this operation
    */
  final case class DeprecateResource(id: Id, rev: Long, meta: Meta, tags: Set[String])
      extends ResourceCommand
      with Versioned
}

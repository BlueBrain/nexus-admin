package ch.epfl.bluebrain.nexus.admin.kafka

import ch.epfl.bluebrain.nexus.admin.client.types.events.{OrganizationEvent, ProjectEvent}
import ch.epfl.bluebrain.nexus.service.kafka.key.Key

object keys {

  /**
    * [[Key]] implementation for [[ProjectEvent]]
    */
  implicit val projectEventKey: Key[ProjectEvent] = Key.key(_.id.toString)

  /**
    * [[Key]] implementation for [[OrganizationEvent]]
    */
  implicit val organizationEventKey: Key[OrganizationEvent] = Key.key(_.id.toString)

}

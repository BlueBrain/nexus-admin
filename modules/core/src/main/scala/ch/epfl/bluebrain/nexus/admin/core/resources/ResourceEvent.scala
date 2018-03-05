package ch.epfl.bluebrain.nexus.admin.core.resources

import ch.epfl.bluebrain.nexus.admin.core.types.Versioned
import ch.epfl.bluebrain.nexus.admin.ld.IdRef
import ch.epfl.bluebrain.nexus.commons.iam.acls.Meta
import io.circe.Json

sealed trait ResourceEvent extends Product with Serializable with Versioned {
  def id: IdRef
  def meta: Meta
  def tags: Set[String]
}

object ResourceEvent {

  final case class ResourceCreated(id: IdRef, rev: Long, meta: Meta, tags: Set[String], value: Json)
      extends ResourceEvent
  final case class ResourceUpdated(id: IdRef, rev: Long, meta: Meta, tags: Set[String], value: Json)
      extends ResourceEvent
  final case class ResourceDeprecated(id: IdRef, rev: Long, meta: Meta, tags: Set[String]) extends ResourceEvent

}

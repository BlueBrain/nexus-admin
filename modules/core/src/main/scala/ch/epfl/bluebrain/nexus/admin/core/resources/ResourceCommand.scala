package ch.epfl.bluebrain.nexus.admin.core.resources

import ch.epfl.bluebrain.nexus.admin.core.types.Versioned
import ch.epfl.bluebrain.nexus.admin.ld.IdRef
import ch.epfl.bluebrain.nexus.commons.iam.acls.Meta
import io.circe.Json

sealed trait ResourceCommand extends Product with Serializable {
  def id: IdRef
  def meta: Meta
  def tags: Set[String]
}

object ResourceCommand {

  final case class CreateResource(id: IdRef, meta: Meta, tags: Set[String], value: Json) extends ResourceCommand

  final case class UpdateResource(id: IdRef, rev: Long, meta: Meta, tags: Set[String], value: Json)
      extends ResourceCommand
      with Versioned

  final case class DeprecateResource(id: IdRef, rev: Long, meta: Meta, tags: Set[String])
      extends ResourceCommand
      with Versioned
}

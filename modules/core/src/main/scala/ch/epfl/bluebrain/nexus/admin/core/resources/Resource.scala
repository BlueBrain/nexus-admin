package ch.epfl.bluebrain.nexus.admin.core.resources

import ch.epfl.bluebrain.nexus.admin.core.types.{Ref, Versioned}
import io.circe.Json

final case class Resource[A](id: Ref[A], rev: Long, value: Json, deprecated: Boolean) extends Versioned

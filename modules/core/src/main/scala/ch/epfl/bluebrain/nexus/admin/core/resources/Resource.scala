package ch.epfl.bluebrain.nexus.admin.core.resources

import ch.epfl.bluebrain.nexus.admin.core.types.{Ref, Versioned}
import io.circe.Json

/**
  * Data type representing the state of any resource.
  *
  * @param id         a identifier for the resource
  * @param label      the label (segment) of the resource
  * @param uuid       the permanent identifier for the resource
  * @param rev        the selected revision for the resource
  * @param value      the json payload of the resource
  * @param deprecated the deprecation status of the resource
  * @tparam A the generic type of the id's ''reference''
  */
final case class Resource[A](id: Ref[A], label: String, uuid: String, rev: Long, value: Json, deprecated: Boolean)
    extends Versioned

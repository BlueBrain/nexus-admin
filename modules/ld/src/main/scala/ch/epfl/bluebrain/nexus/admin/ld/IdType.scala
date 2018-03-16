package ch.epfl.bluebrain.nexus.admin.ld

import ch.epfl.bluebrain.nexus.admin.refined.ld.Id

/**
  * Base enumeration type for IdType classes.
  */
sealed trait IdType extends Product with Serializable

/**
  * An empty ''id'' due to graph recursion
  */
final case object Empty extends IdType

/**
  * An invalid ''id'' due to marshalling
  */
final case class Invalid(str: String) extends IdType

/**
  * An id which is a [[Id]] (uri)
  *
  * @param value the uri Id
  */
final case class IdTypeUri(value: Id) extends IdType

/**
  * An id which is a Blank Node (does not have a uri)
  *
  * @param value the blank node auto-generated string
  */
final case class IdTypeBlank(value: String) extends IdType

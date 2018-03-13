package ch.epfl.bluebrain.nexus.admin.query

import io.circe.Decoder

/**
  * Enumeration type for supported resources.
  */
trait QueryResource extends Product with Serializable

object QueryResource {

  /**
    * Projects resources
    */
  final case object Projects extends QueryResource

  /**
    * Schema resources
    */
  final case object Schemas extends QueryResource

  /**
    * Context resources
    */
  final case object Contexts extends QueryResource

  /**
    * Instance resources
    */
  final case object Instances extends QueryResource

  def fromString(value: String): Option[QueryResource] = value match {
    case "projects"  => Some(Projects)
    case "schemas"   => Some(Schemas)
    case "contexts"  => Some(Contexts)
    case "instances" => Some(Instances)
    case _           => None
  }

  implicit final val queryResourceDecoder: Decoder[QueryResource] =
    Decoder.decodeString.emap(v => fromString(v).toRight(s"Could not find QueryResource for '$v'"))
}

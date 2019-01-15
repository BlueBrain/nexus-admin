package ch.epfl.bluebrain.nexus.admin

import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder
import shapeless.Typeable

/**
  * An error representation that can be uniquely identified by its code.
  *
  * @param `@type`    the unique code for this error
  * @param message    an optional detailed error message
  * @param `@context` the JSON-LD context
  */
final case class Error(`@type`: String, message: Option[String], `@context`: String) {
  def code: String = `@type`
}

object Error {

  /**
    * Provides the class name for ''A''s that have a [[shapeless.Typeable]] typeclass instance.
    *
    * @tparam A a generic type parameter
    * @return class name of A
    */
  final def classNameOf[A: Typeable]: String = {
    val describe = implicitly[Typeable[A]].describe
    describe.substring(0, describe.lastIndexOf('.'))
  }

  implicit val errorDecoder: Decoder[Error] = deriveDecoder[Error]
}

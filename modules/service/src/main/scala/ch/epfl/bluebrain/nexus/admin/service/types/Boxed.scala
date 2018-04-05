package ch.epfl.bluebrain.nexus.admin.service.types

import io.circe.Encoder

/**
  * A boxed value representation that also provides a collection of related resource addresses.
  *
  * @param value the boxed value
  * @param links the collection of related resource addresses
  * @tparam A the type of boxed value
  */
final case class Boxed[A](value: A, links: Links)

object Boxed {
  implicit def boxedEncoder[A](implicit A: Encoder[A], L: Encoder[Links]): Encoder[Boxed[A]] =
    Encoder.instance(boxed => A(boxed.value).mapObject(jObj => jObj.add("links", L(boxed.links))))

}

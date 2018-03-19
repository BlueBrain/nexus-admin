package ch.epfl.bluebrain.nexus.admin.refined

import eu.timepit.refined.api.{RefType, Validate}
import shapeless.Typeable

object typeable extends TypeableInstances

private[refined] trait TypeableInstances {
  /**
    * `Typeable` instance for refined types.
    */
  implicit def refTypeTypeable[F[_, _], T, P](implicit rt: RefType[F],
    V: Validate[T, P],
    T: Typeable[T],
    P: Typeable[P]): Typeable[F[T, P]] =
    new Typeable[F[T, P]] {
      override def cast(t: Any): Option[F[T, P]] =
        T.cast(t).flatMap(casted => rt.refine[P](casted).toOption)
      override def describe: String = s"Refined[${T.describe}, ${P.describe}]"
    }
}

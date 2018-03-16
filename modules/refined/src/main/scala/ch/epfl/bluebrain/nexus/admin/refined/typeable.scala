package ch.epfl.bluebrain.nexus.admin.refined

import eu.timepit.refined.api.RefType.applyRef
import eu.timepit.refined.api.{Refined, Validate}
import shapeless.Typeable

object typeable extends TypeableInstances

private[refined] trait TypeableInstances extends TypeableInstancesLowPriority {
  implicit final def typeableOfRefinedType[FTP, T, P](implicit ev: Refined[T, P] =:= FTP,
                                                      V: Validate[T, P],
                                                      T: Typeable[T],
                                                      P: Typeable[P]): Typeable[FTP] = {
    new Typeable[FTP] {
      override def cast(t: Any): Option[FTP] =
        T.cast(t).flatMap(v => applyRef[FTP](v).toOption)
      override def describe: String = s"Refined[${T.describe}, ${P.describe}]"
    }
  }
}

private[refined] trait TypeableInstancesLowPriority {
  implicit final def typeableOfRefinedTypeLowPriority[FTP, T, P](implicit ev: Refined[T, P] =:= FTP,
                                                                 V: Validate[T, P],
                                                                 T: Typeable[T]): Typeable[FTP] = {
    new Typeable[FTP] {
      override def cast(t: Any): Option[FTP] =
        T.cast(t).flatMap(v => applyRef[FTP](v).toOption)
      override def describe: String = s"Refined[${T.describe}, _]"
    }
  }
}

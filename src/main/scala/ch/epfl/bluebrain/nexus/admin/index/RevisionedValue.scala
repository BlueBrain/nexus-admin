package ch.epfl.bluebrain.nexus.admin.index
import akka.cluster.ddata.LWWRegister.Clock

/**
  * Class representing a value with revision.
  *
  * @param rev    the revision
  * @param value  the value
  */
final case class RevisionedValue[A](rev: Long, value: A)

object RevisionedValue {

  private[index] def revisionedValueClock[A]: Clock[RevisionedValue[A]] =
    (_: Long, value: RevisionedValue[A]) => value.rev
}

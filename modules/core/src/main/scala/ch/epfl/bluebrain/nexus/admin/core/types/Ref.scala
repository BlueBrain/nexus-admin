package ch.epfl.bluebrain.nexus.admin.core.types

import ch.epfl.bluebrain.nexus.admin.ld.{IdRef, IdResolvable}
import ch.epfl.bluebrain.nexus.admin.refined.project.{ProjectReference, _}
import eu.timepit.refined.auto._

/**
  * A reference of a a generic type with an evidence of [[IdResolvable]].
  * This means than ''A'' can be converted to a [[IdRef]]
  *
  * @param value the instance of A
  * @tparam A the generic type of the ''value''
  */
@SuppressWarnings(Array("UnusedMethodParameter"))
final case class Ref[A: IdResolvable](value: A)

object Ref {
  // $COVERAGE-OFF$
  final implicit val refToResolvable: IdResolvable[ProjectReference] = (a: ProjectReference) => {
    IdRef("projects", "https://nexus.example.ch/v1/projects/", a)
  }
  final implicit def aToRef[A: IdResolvable](value: A): Ref[A] = Ref(value)
  // $COVERAGE-ON$
}

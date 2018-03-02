package ch.epfl.bluebrain.nexus.admin.ld

import ch.epfl.bluebrain.nexus.admin.refined.project.ProjectReference
import ch.epfl.bluebrain.nexus.admin.refined.project._
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
  final implicit val redToResolvable: IdResolvable[ProjectReference] = (a: ProjectReference) => {
    IdRef("projects", "https://nexus.example.ch/v1/projects/", a)
  }
  // $COVERAGE-ON$
}

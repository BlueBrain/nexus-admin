package ch.epfl.bluebrain.nexus.admin.core.types

import ch.epfl.bluebrain.nexus.admin.core.config.AppConfig.ProjectsConfig
import ch.epfl.bluebrain.nexus.admin.ld.{IdRef, IdResolvable}
import ch.epfl.bluebrain.nexus.admin.refined.project.{ProjectReference, _}
import eu.timepit.refined.auto._

/**
  * A reference of a generic type with an evidence of [[IdResolvable]].
  * This means than ''A'' can be converted to a [[IdRef]]
  *
  * @param value the instance of A
  * @tparam A the generic type of the ''value''
  */
@SuppressWarnings(Array("UnusedMethodParameter"))
final case class Ref[A: IdResolvable](value: A)

object Ref {

  /**
    * Builds a [[IdResolvable]] from the available ''config''
    *
    * @param config the implicitly available project specific settings
    */
  final implicit def refToResolvable(implicit config: ProjectsConfig): IdResolvable[ProjectReference] =
    (a: ProjectReference) => IdRef("projects", config.namespace, a)

  final implicit def aToRef[A: IdResolvable](value: A): Ref[A] = Ref(value)
}

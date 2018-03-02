package ch.epfl.bluebrain.nexus.admin.ld

/**
  * Trait which defines a way to convert a generic ''A'' into an [[IdRef]]
  * @tparam A the generic type to be converted into [[IdRef]]
  */
trait IdResolvable[A] extends (A => IdRef)

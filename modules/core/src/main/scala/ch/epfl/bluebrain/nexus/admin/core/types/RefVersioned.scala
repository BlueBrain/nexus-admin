package ch.epfl.bluebrain.nexus.admin.core.types

/**
  * A versioned reference of a ''resource''
  *
  * @param id  the reference value
  * @param rev the selected revision of the resource
  * @tparam A the generic type of the id's ''reference''
  */
final case class RefVersioned[A](id: Ref[A], rev: Long) extends Versioned

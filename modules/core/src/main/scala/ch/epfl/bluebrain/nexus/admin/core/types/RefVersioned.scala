package ch.epfl.bluebrain.nexus.admin.core.types

final case class RefVersioned[A](id: Ref[A], rev: Long) extends Versioned

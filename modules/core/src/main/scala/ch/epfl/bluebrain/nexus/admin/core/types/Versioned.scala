package ch.epfl.bluebrain.nexus.admin.core.types

/**
  * Trait used to represent a versioned element
  */
trait Versioned {

  /**
    * @return the version
    */
  def rev: Long
}

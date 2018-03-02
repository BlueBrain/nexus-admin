package ch.epfl.bluebrain.nexus.admin.ld

import ch.epfl.bluebrain.nexus.admin.ld.Prefix.randomPrefixName
import ch.epfl.bluebrain.nexus.admin.refined.ld._

/**
  * Helper to build a [[IdRef]]
  *
  * @param name  the prefix name
  * @param value the value to which the name expands
  */
class IdRefBuilder(name: PrefixName, value: PrefixValue) {

  /**
    * Change the prefix name of the builder
    * @param otherName the new prefix name
    * @return a new [[IdRefBuilder]]
    */
  def withPrefix(otherName: PrefixName): IdRefBuilder =
    IdRefBuilder(otherName, value)

  /**
    * Build the [[IdRef]] with the provided ''reference''.
    *
    * @param reference the reference component of the curie
    */
  def build(reference: Reference): IdRef = IdRef(name, value, reference)

}

object IdRefBuilder {
  final def apply(name: PrefixName, value: PrefixValue): IdRefBuilder = new IdRefBuilder(name, value)
  final def apply(prefix: Prefix): IdRefBuilder                       = apply(prefix.name, prefix.value)
  final def apply(value: PrefixValue): IdRefBuilder                   = apply(Prefix(randomPrefixName(), value))

}

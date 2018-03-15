package ch.epfl.bluebrain.nexus.admin.ld

import ch.epfl.bluebrain.nexus.admin.ld.PrefixMapping.randomPrefixName
import ch.epfl.bluebrain.nexus.admin.refined.ld._

/**
  * Helper to build a [[IdRef]]
  *
  * @param name  the prefix name
  * @param value the value to which the name expands
  */
class IdRefBuilder(name: Prefix, val value: Namespace) {

  /**
    * Change the prefix name of the builder
    * @param otherName the new prefix name
    * @return a new [[IdRefBuilder]]
    */
  def withPrefix(otherName: Prefix): IdRefBuilder =
    IdRefBuilder(otherName, value)

  /**
    * Build the [[IdRef]] with the provided ''reference''.
    *
    * @param reference the reference component of the curie
    */
  def build(reference: Reference): IdRef = IdRef(name, value, reference)

}

object IdRefBuilder {
  final def apply(name: Prefix, value: Namespace): IdRefBuilder = new IdRefBuilder(name, value)
  final def apply(prefix: PrefixMapping): IdRefBuilder          = apply(prefix.prefix, prefix.namespace)
  final def apply(value: Namespace): IdRefBuilder               = apply(PrefixMapping(randomPrefixName(), value))

}

package ch.epfl.bluebrain.nexus.admin.ld

import ch.epfl.bluebrain.nexus.admin.ld.PrefixMapping.randomPrefix
import ch.epfl.bluebrain.nexus.admin.refined.ld._

/**
  * Helper to build a [[IdRef]]
  *
  * @param prefixBuilder    the prefix (left side of a PrefixMapping)
  * @param namespaceBuilder the namespace (right side of a PrefixMapping). Is the value to which the name expands, as well as the prefix component of the curie.
  */
class IdRefBuilder(val prefixBuilder: Prefix, val namespaceBuilder: Namespace) {

  /**
    * Change the prefix of the builder
    *
    * @param otherPrefix the new prefix
    * @return a new [[IdRefBuilder]]
    */
  def withPrefix(otherPrefix: Prefix): IdRefBuilder =
    IdRefBuilder(otherPrefix, namespaceBuilder)

  /**
    * Build the [[IdRef]] with the provided ''reference''.
    *
    * @param reference the reference component of the curie
    */
  def build(reference: Reference): IdRef = IdRef(prefixBuilder, namespaceBuilder, reference)

}

object IdRefBuilder {
  final def apply(name: Prefix, value: Namespace): IdRefBuilder = new IdRefBuilder(name, value)
  final def apply(prefix: PrefixMapping): IdRefBuilder          = apply(prefix.prefix, prefix.namespace)
  final def apply(value: Namespace): IdRefBuilder               = apply(PrefixMapping(randomPrefix(), value))

}

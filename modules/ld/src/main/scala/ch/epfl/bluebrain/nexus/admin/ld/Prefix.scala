package ch.epfl.bluebrain.nexus.admin.ld

import ch.epfl.bluebrain.nexus.admin.ld.uri.{PrefixName, PrefixValue}

/**
  * A single name to uri mapping (an entry of a prefix mapping).
  *
  * @param name  the prefix name
  * @param value the value to which the name expands
  */
final case class Prefix(name: PrefixName, value: PrefixValue)
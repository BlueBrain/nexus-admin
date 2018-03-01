package ch.epfl.bluebrain.nexus.admin.ld

import ch.epfl.bluebrain.nexus.admin.ld.uri.{PrefixName, Reference}

/**
  * A curie representation as defined in https://www.w3.org/TR/curie/.
  *
  * @param prefix    the prefix component of the curie
  * @param reference the reference component of the curie
  */
final case class Curie(prefix: PrefixName, reference: Reference)
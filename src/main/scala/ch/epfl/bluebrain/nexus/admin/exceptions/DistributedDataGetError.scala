package ch.epfl.bluebrain.nexus.admin.exceptions

import ch.epfl.bluebrain.nexus.commons.types.Err

/**
  * Exception signalling error when fetching a key from Akka Distributed Data
  *
  * @param key key to fetch
  */
@SuppressWarnings(Array("IncorrectlyNamedExceptions"))
final case class DistributedDataGetError(key: String) extends Err(s"Error fetching Distributed Data key $key")

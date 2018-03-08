package ch.epfl.bluebrain.nexus.admin.core.persistence

import akka.persistence.journal.{Tagged, WriteEventAdapter}
import ch.epfl.bluebrain.nexus.admin.core.resources.ResourceEvent

/**
  * A tagging event adapter that adds tags to discriminate between event hierarchies.
  */
class TaggingAdapter extends WriteEventAdapter {

  override def manifest(event: Any): String = ""

  override def toJournal(event: Any): Any = event match {
    case ev: ResourceEvent => Tagged(ev, ev.tags)
    case _                 => event
  }
}

package ch.epfl.bluebrain.nexus.admin.persistence

import akka.persistence.journal.{Tagged, WriteEventAdapter}
import ch.epfl.bluebrain.nexus.admin.projects.ProjectEvent

/**
  * A tagging event adapter that adds tags to discriminate between event hierarchies.
  */
class TaggingAdapter extends WriteEventAdapter {

  override def manifest(event: Any): String = ""

  override def toJournal(event: Any): Any = event match {
    case pe: ProjectEvent => Tagged(pe, Set("project"))
    // TODO: case po: OrganizationEvent => Tagged(po, Set("organization"))
    case _ => event
  }
}

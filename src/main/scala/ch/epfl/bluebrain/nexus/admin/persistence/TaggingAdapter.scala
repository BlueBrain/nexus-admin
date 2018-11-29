package ch.epfl.bluebrain.nexus.admin.persistence

import akka.persistence.journal.{Tagged, WriteEventAdapter}
import ch.epfl.bluebrain.nexus.admin.organizations.OrganizationEvent
import ch.epfl.bluebrain.nexus.admin.projects.ProjectEvent

/**
  * A tagging event adapter that adds tags to discriminate between event hierarchies.
  */
class TaggingAdapter extends WriteEventAdapter {

  override def manifest(event: Any): String = event match {
    case _: ProjectEvent      => "project"
    case _: OrganizationEvent => "organization"
  }

  override def toJournal(event: Any): Any = event match {
    case pe: ProjectEvent      => Tagged(pe, Set("project"))
    case po: OrganizationEvent => Tagged(po, Set("organization"))
    case _                     => event
  }
}

package ch.epfl.bluebrain.nexus.admin.organizations
import java.util.UUID

final case class Organization(id: UUID, label: String, description: String)

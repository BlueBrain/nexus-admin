package ch.epfl.bluebrain.nexus.admin.core.persistence

import ch.epfl.bluebrain.nexus.admin.core.resources.ResourceEvent
import ch.epfl.bluebrain.nexus.admin.ld.DecomposableIdOps._
import ch.epfl.bluebrain.nexus.service.serialization.AkkaCoproductSerializer
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.auto._
import io.circe.java8.time._
import shapeless.{:+:, CNil}

/**
  * Akka ''SerializerWithStringManifest'' class definition for all events.
  * The serializer provides the types available for serialization.
  */
object Serializer {

  private implicit val config: Configuration = Configuration.default.withDiscriminator("type")

  class EventSerializer extends AkkaCoproductSerializer[ResourceEvent :+: CNil](1215)

}

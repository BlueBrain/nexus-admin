package ch.epfl.bluebrain.nexus.admin.ld
import java.time.Instant

import ch.epfl.bluebrain.nexus.admin.ld.Const._
import ch.epfl.bluebrain.nexus.admin.refined.ld.Id
import io.circe.Json

object JsonLdOps {

  /**
    * Interface syntax to expose new functionality into Uri type
    *
    * @param value the [[Id]]
    */
  implicit class IdJsonLDSupportOps(value: Id) {

    /**
      * Method exposed on Uri instances.
      *
      * @return Json which contains the key @id and the value uri ('{"@id": "uri"}')
      */
    def jsonLd: Json = Json.obj(`@id` -> Json.fromString(value.toString()))
  }

  /**
    * Interface syntax to expose new functionality into Instant type
    *
    * @param value the [[Instant]]
    */
  implicit class InstantJsonLDSupportOps(value: Instant) {

    /**
      * Method exposed on Instant instances.
      *
      * @return Json representing a dateTime ('{"@type": "http://www.w3.org/2001/XMLSchema#dateTime", "@value":"2015-01-25T12:34:56Z"}')
      */
    def jsonLd: Json =
      Json.obj(`@type` -> Json.fromString(xmlSchema.dateTime.value), `@value` -> Json.fromString(value.toString))
  }
}

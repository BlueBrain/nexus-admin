package ch.epfl.bluebrain.nexus.admin.service.encoders

import cats.syntax.show._
import ch.epfl.bluebrain.nexus.admin.core.resources.Resource
import ch.epfl.bluebrain.nexus.admin.ld.Const.{nxv, projectContext, schema}
import ch.epfl.bluebrain.nexus.admin.ld.JsonLD._
import ch.epfl.bluebrain.nexus.admin.refined.ld.Namespace
import ch.epfl.bluebrain.nexus.admin.refined.organization.OrganizationReference
import io.circe.{Encoder, Json}

object organization {

  /**
    * Constructs implicit encoder for organization resource which creates JSON-LD representation from underlying RDF graph.
    * @param organizationNamespace organization namespace used to create `@id`
    * @return encoder for organization resource
    */
  implicit def organizationEncoder(
      implicit organizationNamespace: Namespace): Encoder[Resource[OrganizationReference]] =
    Encoder.encodeJson.contramap { resource =>
      val ld =
        resource.value
          .appendContext(projectContext)

      Json.obj(
        "@id"   -> Json.fromString(s"${organizationNamespace.value}${resource.id.value.value}"),
        "@type" -> Json.fromString(nxv.Organization.curie.show),
        "label" -> Json.fromString(resource.id.value.value),
        "name" -> Json.fromString(
          ld.value[String](schema.name)
            .getOrElse(throw new IllegalArgumentException(
              s"${resource.value.noSpaces} did not contain predicate ${schema.name.value}"))),
        "_rev"        -> Json.fromLong(resource.rev),
        "_deprecated" -> Json.fromBoolean(resource.deprecated),
        "_uuid"       -> Json.fromString(resource.uuid)
      )

    }

}

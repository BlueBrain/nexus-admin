package ch.epfl.bluebrain.nexus.admin.service.encoders

import cats.syntax.show._
import ch.epfl.bluebrain.nexus.admin.core.resources.Resource
import ch.epfl.bluebrain.nexus.admin.ld.Const
import ch.epfl.bluebrain.nexus.admin.ld.Const.{nxv, resourceContext, schema}
import ch.epfl.bluebrain.nexus.admin.ld.JsonLD._
import ch.epfl.bluebrain.nexus.admin.refined.ld.Namespace
import ch.epfl.bluebrain.nexus.admin.refined.organization.OrganizationReference
import eu.timepit.refined.auto._
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
          .appendContext(resourceContext)

      Json.obj(
        Const.`@id`               -> Json.fromString(s"${organizationNamespace.value}${resource.id.value.value}"),
        Const.`@type`             -> Json.fromString(nxv.Organization.curie.show),
        nxv.label.reference.value -> Json.fromString(resource.id.value),
        schema.name.reference.value -> Json.fromString(
          ld.value[String](schema.name)
            .getOrElse(throw new IllegalArgumentException(
              s"${resource.value.noSpaces} did not contain predicate ${schema.name.value}"))),
        nxv.rev.reference.value        -> Json.fromLong(resource.rev),
        nxv.deprecated.reference.value -> Json.fromBoolean(resource.deprecated),
        nxv.uuid.reference.value       -> Json.fromString(resource.uuid)
      )

    }

}

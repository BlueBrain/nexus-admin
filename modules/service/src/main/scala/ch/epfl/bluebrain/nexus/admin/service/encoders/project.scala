package ch.epfl.bluebrain.nexus.admin.service.encoders

import cats.syntax.show._
import ch.epfl.bluebrain.nexus.admin.core.resources.Resource
import ch.epfl.bluebrain.nexus.admin.ld.Const.{nxv, resourceContext, schema}
import ch.epfl.bluebrain.nexus.admin.ld.{Const, JsonLD}
import ch.epfl.bluebrain.nexus.admin.ld.JsonLD._
import ch.epfl.bluebrain.nexus.admin.refined.ld.Namespace
import ch.epfl.bluebrain.nexus.admin.refined.project._
import eu.timepit.refined.auto._
import io.circe.{Encoder, Json}

object project {

  private def retrieveMappings(jsonLD: JsonLD): List[Json] =
    jsonLD
      .down(nxv.prefixMappings)
      .foldLeft(List.empty[Json]) { (acc, c) =>
        (for {
          prefix <- c.value[String](nxv.prefix)
          ns     <- c.value[String](nxv.namespace)
        } yield
          Json.obj(
            nxv.prefix.reference.value    -> Json.fromString(prefix),
            nxv.namespace.reference.value -> Json.fromString(ns)
          )) match {
          case Some(v) => v :: acc
          case None    => acc
        }
      }
      .reverse

  /**
    * Constructs implicit encoder for project resource which creates JSON-LD representation from underlying RDF graph.
    * @param projectNamespace projects namespace used to create `@id`
    * @return encoder for project resource
    */
  implicit def projectEncoder(implicit projectNamespace: Namespace): Encoder[Resource[ProjectReference]] =
    Encoder.encodeJson.contramap { resource =>
      val ld =
        resource.value
          .appendContext(resourceContext)

      Json.obj(
        Const.`@id`               -> Json.fromString(s"${projectNamespace.value}${resource.id.value.show}"),
        Const.`@type`             -> Json.fromString(nxv.Project.curie.show),
        nxv.label.reference.value -> Json.fromString(resource.id.value.projectLabel),
        nxv.name.reference.value -> Json.fromString(
          ld.value[String](schema.name)
            .getOrElse(throw new IllegalArgumentException(
              s"${resource.value.noSpaces} did not contain predicate ${schema.name.value}"))),
        nxv.base.reference.value -> Json.fromString(
          ld.value[String](nxv.base)
            .getOrElse(throw new IllegalArgumentException(
              s"${resource.value.noSpaces} did not contain predicate ${nxv.base.value}"))),
        nxv.prefixMappings.reference.value -> Json.fromValues(retrieveMappings(ld)),
        nxv.rev.reference.value            -> Json.fromLong(resource.rev),
        nxv.deprecated.reference.value     -> Json.fromBoolean(resource.deprecated),
        nxv.uuid.reference.value           -> Json.fromString(resource.uuid)
      )

    }
}

package ch.epfl.bluebrain.nexus.admin.service.encoders

import cats.syntax.show._
import ch.epfl.bluebrain.nexus.admin.core.resources.Resource
import ch.epfl.bluebrain.nexus.admin.ld.Const.{nxv, projectContext, schema}
import ch.epfl.bluebrain.nexus.admin.ld.JsonLD
import ch.epfl.bluebrain.nexus.admin.ld.JsonLD._
import ch.epfl.bluebrain.nexus.admin.refined.ld.Namespace
import ch.epfl.bluebrain.nexus.admin.refined.project.ProjectReference
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
            "prefix"    -> Json.fromString(prefix),
            "namespace" -> Json.fromString(ns)
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
          .appendContext(projectContext)

      Json.obj(
        "@id"   -> Json.fromString(s"${projectNamespace.value}${resource.id.value.value}"),
        "@type" -> Json.fromString(nxv.Project.curie.show),
        "label" -> Json.fromString(resource.id.value.value),
        "name" -> Json.fromString(
          ld.value[String](schema.name)
            .getOrElse(throw new IllegalArgumentException(
              s"${resource.value.noSpaces} did not contain predicate ${schema.name.value}"))),
        "base" -> Json.fromString(
          ld.value[String](nxv.base)
            .getOrElse(throw new IllegalArgumentException(
              s"${resource.value.noSpaces} did not contain predicate ${nxv.base.value}"))),
        "prefixMappings" -> Json.fromValues(retrieveMappings(ld)),
        "_rev"           -> Json.fromLong(resource.rev),
        "_deprecated"    -> Json.fromBoolean(resource.deprecated),
        "_uuid"          -> Json.fromString(resource.uuid)
      )

    }
}

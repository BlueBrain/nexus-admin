package ch.epfl.bluebrain.nexus.admin.types

import java.time.Instant

import ch.epfl.bluebrain.nexus.admin.config.Contexts._
import ch.epfl.bluebrain.nexus.admin.config.Vocabulary.nxv
import ch.epfl.bluebrain.nexus.admin.marshallers.instances._
import ch.epfl.bluebrain.nexus.commons.types.identity.Identity
import ch.epfl.bluebrain.nexus.rdf.Iri.AbsoluteIri
import io.circe.syntax._
import io.circe.{Encoder, Json}

/**
  * The metadata information for any resource in the service
  *
  * @param id        the id of the resource
  * @param rev       the revision
  * @param types     the types of the resource
  * @param createdAt the creation date of the resource
  * @param createdBy the identity that created the resource
  * @param updatedAt the last update date of the resource
  * @param updatedBy the identity that performed the last update to the resource
  * @param value     the resource value
  */
final case class ResourceF[A](
    id: AbsoluteIri,
    rev: Long,
    types: Set[AbsoluteIri],
    createdAt: Instant,
    createdBy: Identity,
    updatedAt: Instant,
    updatedBy: Identity,
    value: A
) {

  /**
    * Creates a new [[ResourceF]] changing the value using the provided ''f'' function.
    *
    * @param f a function to convert the current value
    * @tparam B the generic type of the resulting value field
    */
  def map[B](f: A => B): ResourceF[B] =
    copy(value = f(value))

  /**
    * Converts the current [[ResourceF]] to a [[ResourceF]] where the value is of type Unit.
    */
  def discard: ResourceF[Unit] =
    map(_ => ())
}

object ResourceF {

  /**
    * Constructs a [[ResourceF]] where the value is of type Unit
    *
    * @param id        the identifier of the resource
    * @param rev       the revision of the resource
    * @param types     the types of the resource
    * @param createdAt the instant when the resource was created
    * @param createdBy the identity that created the resource
    * @param updatedAt the instant when the resource was updated
    * @param updatedBy the identity that updated the resource
    */
  def unit(
      id: AbsoluteIri,
      rev: Long,
      types: Set[AbsoluteIri],
      createdAt: Instant,
      createdBy: Identity,
      updatedAt: Instant,
      updatedBy: Identity
  ): ResourceF[Unit] =
    ResourceF(id, rev, types, createdAt, createdBy, updatedAt, updatedBy, ())

  implicit val resourceMetaEncoder: Encoder[ResourceMetadata] =
    Encoder.encodeJson.contramap {
      case ResourceF(id, rev, types, createdAt, createdBy, updatedAt, updatedBy, _: Unit) =>
        Json.obj(
          "@context"           -> Json.arr(resourceCtxUri.asJson, adminCtxUri.asJson),
          "@id"                -> id.asJson,
          "@type"              -> Json.arr(types.map(t => Json.fromString(lastSegment(t).getOrElse(t.asString))).toSeq: _*),
          nxv.rev.prefix       -> Json.fromLong(rev),
          nxv.createdBy.prefix -> createdBy.id.asJson,
          nxv.updatedBy.prefix -> updatedBy.id.asJson,
          nxv.createdAt.prefix -> Json.fromString(createdAt.toString),
          nxv.updatedAt.prefix -> Json.fromString(updatedAt.toString)
        )
    }

  private def lastSegment(iri: AbsoluteIri): Option[String] =
    iri.path.head match {
      case segment: String => Some(segment)
      case _               => None
    }
}

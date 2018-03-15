package ch.epfl.bluebrain.nexus.admin.refined

import akka.http.scaladsl.model.Uri.Path.Segment
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model.{Uri => AkkaUri}
import eu.timepit.refined._
import eu.timepit.refined.api.RefType._
import eu.timepit.refined.api.{Refined, Validate}
import eu.timepit.refined.string.MatchesRegex

import scala.util.Try

@SuppressWarnings(Array("EmptyCaseClass"))
object ld {

  /**
    * Refined type for prefix (left side on a PrefixMapping).
    */
  type Prefix = String Refined MatchesRegex[W.`"[a-zA-Z_][a-zA-Z0-9-_.]*"`.T]

  /**
    * Refined type for the namespace (right side on a PrefixMapping).
    */
  type Namespace = String Refined PrefixUri

  /**
    * Refined type for curie references.
    */
  type Reference = String Refined IRelativeRef

  /**
    * Refined type for RDF ids that can be decomposed into a [[Namespace]] and a [[Reference]].
    */
  type Id = String Refined Uri

  final case class Uri()

  object Uri {
    private[ld] def unsafeDecompose(s: String): (Namespace, Reference) = {
      val uri = AkkaUri(s)
      if (!uri.isAbsolute) throw new IllegalArgumentException()
      else
        uri.query() match {
          case Query.Empty =>
            uri.fragment match {
              case Some(fragment) if !fragment.isEmpty =>
                val value: Namespace     = refinedRefType.unsafeWrap(s"${uri.withoutFragment}#")
                val reference: Reference = refinedRefType.unsafeWrap(fragment)
                value -> reference
              case Some(_) => throw new IllegalArgumentException(s"The uri '$uri' contains an empty fragment")
              case _ =>
                uri.path.reverse match {
                  case Segment(head, tail) =>
                    val value: Namespace     = refinedRefType.unsafeWrap(uri.copy(path = tail.reverse).toString())
                    val reference: Reference = refinedRefType.unsafeWrap(head)
                    value -> reference
                  case _ =>
                    throw new IllegalArgumentException(s"The uri '$uri' contains has an invalid path '${uri.path}'")
                }
            }
          case _ =>
            throw new IllegalArgumentException(s"The uri '$uri' contains a query parameter '${uri.queryString()}'")
        }
    }

    /**
      * Interface syntax to expose new functionality into [[Namespace]], [[Reference]] tuple type.
      *
      * @param value the instance of a [[Tuple2]] of [[Namespace]] and [[Reference]]
      */
    implicit class ToIdSyntax(value: (Namespace, Reference)) {

      /**
        * Build a [[id]] out of an instance of [[Namespace]] and [[Reference]]
        */
      def id: Id = {
        val (namespace, reference) = value
        refinedRefType.unsafeWrap(s"$namespace$reference")
      }
    }

    /**
      * Interface syntax to expose new functionality into [[Id]] type.
      *
      * @param value the instance of a [[Id]]
      */
    implicit class IdSyntax(value: Id) {

      /**
        * Decompose the ''value'' into two parts, the [[Namespace]] and the [[Reference]]
        */
      def decompose: (Namespace, Reference) =
        unsafeDecompose(value.value)
    }

    final implicit def uriValidate: Validate.Plain[String, Uri] =
      Validate.fromPartial(unsafeDecompose, "ValidConvertibleUri", Uri())
  }

  final case class PrefixUri()

  object PrefixUri {
    final implicit def prefixUriValidate: Validate.Plain[String, PrefixUri] = {
      def validPrefix(s: String): Boolean = {
        akkaUri(s)
          .map { uri =>
            uri.query() match {
              case Query.Empty =>
                uri.fragment match {
                  case Some(fragment) => fragment.isEmpty
                  case None           => uri.path.endsWithSlash
                }
              case _ => false
            }
          }
          .getOrElse(false)
      }

      Validate.fromPredicate(s => validPrefix(s), s => s"ValidPrefixUri($s)", PrefixUri())
    }
  }

  final case class IRelativeRef()

  object IRelativeRef {
    // TODO: support curie references defined as '"//" iauthority ipath-abempty'
    final implicit def iRelativeRefValidate: Validate.Plain[String, IRelativeRef] = {
      def validIRelativeRef(s: String): Boolean =
        akkaUri(s"http://localhost/$s").map(_ => true).getOrElse(false)

      Validate.fromPredicate(s => validIRelativeRef(s), s => s"ValidIRelativeRef($s)", IRelativeRef())
    }
  }

  private[ld] def akkaUri(s: String): Try[AkkaUri] = Try(AkkaUri(s)).filter(_.isAbsolute)
}

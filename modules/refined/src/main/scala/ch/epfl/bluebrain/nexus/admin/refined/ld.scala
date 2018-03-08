package ch.epfl.bluebrain.nexus.admin.refined

import akka.http.scaladsl.model.Uri.Path.Segment
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model.{Uri => AkkaUri}
import ch.epfl.bluebrain.nexus.admin.refined.ld.Uri._
import ch.epfl.bluebrain.nexus.admin.refined.ld._
import eu.timepit.refined._
import eu.timepit.refined.api.Inference.==>
import eu.timepit.refined.api.RefType._
import eu.timepit.refined.api.{Inference, Refined, Validate}
import eu.timepit.refined.string.MatchesRegex

import scala.util.Try

@SuppressWarnings(Array("EmptyCaseClass"))
object ld extends LdInferences {

  /**
    * Refined type for prefix names.
    */
  type PrefixName = String Refined MatchesRegex[W.`"[a-zA-Z_][a-zA-Z0-9-_.]*"`.T]

  /**
    * Refined type for prefix values.
    */
  type PrefixValue = String Refined PrefixUri

  /**
    * Refined type for curie references.
    */
  type Reference = String Refined IRelativeRef

  /**
    * Refined type for RDF ids.
    */
  type Id = String Refined Uri

  /**
    * Refined type for RDF ids that can be decomposed into a [[PrefixValue]] and a [[Reference]].
    */
  type DecomposableId = String Refined DecomposableUri

  final case class DecomposableUri()

  object DecomposableUri {
    private[ld] def unsafeDecompose(s: String): (PrefixValue, Reference) = {
      val uri = AkkaUri(s)
      if (!uri.isAbsolute) throw new IllegalArgumentException()
      else
        uri.query() match {
          case Query.Empty =>
            uri.fragment match {
              case Some(fragment) if !fragment.isEmpty =>
                val value: PrefixValue   = refinedRefType.unsafeWrap(s"${uri.withoutFragment}#")
                val reference: Reference = refinedRefType.unsafeWrap(fragment)
                value -> reference
              case Some(_) => throw new IllegalArgumentException(s"The uri '$uri' contains an empty fragment")
              case _ =>
                uri.path.reverse match {
                  case Segment(head, tail) =>
                    val value: PrefixValue   = refinedRefType.unsafeWrap(uri.copy(path = tail.reverse).toString())
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
      * Interface syntax to expose new functionality into [[PrefixValue]], [[Reference]] tuple type.
      *
      * @param value the instance of a [[Tuple2]] of [[PrefixValue]] and [[Reference]]
      */
    implicit class ToDecomposableIdSyntax(value: (PrefixValue, Reference)) {
      /**
        * Build a [[DecomposableId]] out of an instance of [[PrefixValue]] and [[Reference]]
        */
      def decomposableId: DecomposableId = {
        val (prefixValue, reference) = value
        refinedRefType.unsafeWrap(s"$prefixValue$reference")
      }
    }

    /**
      * Interface syntax to expose new functionality into [[DecomposableId]] type.
      *
      * @param value the instance of a [[DecomposableId]]
      */
    implicit class DecomposableIdSyntax(value: DecomposableId) {
      /**
        * Decompose the ''value'' into two parts, the [[PrefixValue]] and the [[Reference]]
        */
      def decompose: (PrefixValue, Reference) =
        unsafeDecompose(value.value)
    }

    final implicit def decompUriValidate: Validate.Plain[String, DecomposableUri] =
      Validate.fromPartial(unsafeDecompose, "ValidConvertibleUri", DecomposableUri())
  }

  final case class Uri()

  object Uri {
    private[ld] def akkaUri(s: String): Try[AkkaUri] =
      Try(AkkaUri(s)).filter(_.isAbsolute)

    final implicit def uriValidate: Validate.Plain[String, Uri] = {
      def validUri(s: String): Boolean =
        akkaUri(s).map(_ => true).getOrElse(false)

      Validate.fromPredicate(s => validUri(s), s => s"ValidUri($s)", Uri())
    }
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
}

trait LdInferences {

  final implicit val decompIdInference: DecomposableUri ==> Uri =
    Inference.alwaysValid("A ConvertibleId is always valid Uri")
}

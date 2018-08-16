package ch.epfl.bluebrain.nexus.admin.refined

import ch.epfl.bluebrain.nexus.admin.refined.ld.{AliasOrNamespacePredicate, Uri}
import ch.epfl.bluebrain.nexus.rdf.Iri
import ch.epfl.bluebrain.nexus.rdf.Iri.Path._
import eu.timepit.refined._
import eu.timepit.refined.api.Inference.==>
import eu.timepit.refined.api.RefType._
import eu.timepit.refined.api.{Inference, Refined, Validate}
import eu.timepit.refined.string.MatchesRegex

@SuppressWarnings(Array("EmptyCaseClass"))
object ld extends LdInferences with TypeableInstances {

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
    * Refined type for absolute uri.
    */
  type AliasOrNamespace = String Refined AliasOrNamespacePredicate

  /**
    * Refined type for RDF ids that can be decomposed into a [[Namespace]] and a [[Reference]].
    */
  type Id = String Refined Uri

  final case class Uri()

  object Uri {
    private[ld] def unsafeDecompose(s: String): (Namespace, Reference) = {
      Iri.absolute(s) match {
        case Right(url: Iri.Url) =>
          url.query match {
            case None =>
              url.fragment match {
                case Some(fragment) if fragment.value.nonEmpty =>
                  val value: Namespace     = refinedRefType.unsafeWrap(url.copy(fragment = None).asString)
                  val reference: Reference = refinedRefType.unsafeWrap(fragment.value)
                  value -> reference
                case Some(_) => throw new IllegalArgumentException(s"The URL '$url' contains an empty fragment")
                case None =>
                  url.path match {
                    case Slash(Segment(head, tail)) =>
                      val value: Namespace     = refinedRefType.unsafeWrap(url.copy(path = tail).asString)
                      val reference: Reference = refinedRefType.unsafeWrap(head)
                      value -> reference
                    case Segment(head, tail) =>
                      val value: Namespace     = refinedRefType.unsafeWrap(url.copy(path = tail).asString)
                      val reference: Reference = refinedRefType.unsafeWrap(head)
                      value -> reference
                    case _ =>
                      throw new IllegalArgumentException(s"The URL '$url' contains has an invalid path '${url.path}'")
                  }
              }
            case Some(query) =>
              throw new IllegalArgumentException(s"The URL '$url' contains a query parameter '${query.asString}'")
          }
        case Right(urn: Iri.Urn) =>
          urn.fragment match {
            case Some(fragment) if fragment.value.nonEmpty =>
              val value: Namespace     = refinedRefType.unsafeWrap(urn.copy(fragment = None).asString)
              val reference: Reference = refinedRefType.unsafeWrap(fragment.value)
              value -> reference
            case _ => throw new IllegalArgumentException(s"The URN '$urn' contains an empty fragment")
          }
        case Left(_) => throw new IllegalArgumentException(s"$s is not an IRI")
      }
    }

    private[ld] def unsafeDecompose(s: String, namespace: Namespace): (Namespace, Reference) = {
      if (s.startsWith(namespace.value))
        (namespace, refinedRefType.unsafeWrap(s.stripPrefix(namespace.value)))
      else
        throw new IllegalArgumentException(s"$s doesn't start with required namespace: ${namespace.value}")
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

      /**
        * Decompose the ''value'' into two parts, the [[Namespace]] and the [[Reference]]
        */
      def decompose(namespace: Namespace): (Namespace, Reference) =
        unsafeDecompose(value.value, namespace)
    }

    final implicit def uriValidate: Validate.Plain[String, Uri] =
      Validate.fromPartial(unsafeDecompose, "ValidConvertibleUri", Uri())
  }

  final case class PrefixUri()

  object PrefixUri {
    final implicit def prefixUriValidate: Validate.Plain[String, PrefixUri] =
      Validate.fromPredicate(s => Iri.absolute(s).isRight, s => s"ValidPrefixUri($s)", PrefixUri())
  }

  final case class AliasOrNamespacePredicate()

  object AliasOrNamespacePredicate {
    final implicit def absoluteUriValidate(
        implicit VA: Validate.Plain[String, Uri],
        VB: Validate.Plain[String, PrefixUri]): Validate.Plain[String, AliasOrNamespacePredicate] = {
      Validate.fromPredicate(s => VA.isValid(s) || VB.isValid(s),
                             s => s"ValidAbsoluteUri($s)",
                             AliasOrNamespacePredicate())
    }
  }

  final case class IRelativeRef()

  object IRelativeRef {
    // TODO: support curie references defined as '"//" iauthority ipath-abempty'
    final implicit def iRelativeRefValidate: Validate.Plain[String, IRelativeRef] =
      Validate.fromPredicate(s => Iri.relative(s).isRight, s => s"ValidIRelativeRef($s)", IRelativeRef())
  }

}

trait LdInferences {

  final implicit val idInference: Uri ==> AliasOrNamespacePredicate =
    Inference.alwaysValid("A Id is always valid AbsoluteUri")
}

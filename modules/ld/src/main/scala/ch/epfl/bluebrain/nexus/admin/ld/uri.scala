package ch.epfl.bluebrain.nexus.admin.ld

import akka.http.scaladsl.model.Uri.Query
import eu.timepit.refined.api.{Refined, Validate}
import akka.http.scaladsl.model.{Uri => AkkaUri}
import ch.epfl.bluebrain.nexus.admin.ld.uri.Uri._
import eu.timepit.refined.string.MatchesRegex
import eu.timepit.refined._

import scala.util.Try

@SuppressWarnings(Array("EmptyCaseClass"))
object uri {

  /**
    * Refined type for [[Prefix]] names.
    */
  type PrefixName = String Refined MatchesRegex[W.`"[a-zA-Z_][a-zA-Z0-9-_.]*"`.T]
  /**
    * Refined type for [[Prefix]] values.
    */
  type PrefixValue = String Refined PrefixUri
  /**
    * Refined type for [[Curie]] references.
    */
  type Reference = String Refined IRelativeRef
  /**
    * Refined type for RDF ids.
    */
  type Id = String Refined Uri

  final case class Uri()

  object Uri {
    private[uri] def akkaUri(s: String): Try[AkkaUri] =
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
              case _           => false
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

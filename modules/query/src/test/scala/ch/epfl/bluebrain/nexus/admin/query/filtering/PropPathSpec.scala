package ch.epfl.bluebrain.nexus.admin.query.filtering

import java.io.ByteArrayInputStream

import cats.syntax.show._
import ch.epfl.bluebrain.nexus.admin.ld.Const._
import ch.epfl.bluebrain.nexus.admin.query.filtering.PropPath._
import io.circe.Json
import org.apache.jena.rdf.model.ModelFactory
import org.apache.jena.riot.{Lang, RDFDataMgr}
import org.apache.jena.sparql.path.PathParser
import org.scalatest.{Inspectors, Matchers, TryValues, WordSpecLike}

class PropPathSpec extends WordSpecLike with Matchers with Inspectors with TryValues {

  "A PropPath" should {
    val context = Json.obj(
      "@context" -> Json.obj(
        "nx"  -> Json.fromString(nxv.namespaceBuilder toString ()),
        "rdf" -> Json.fromString(rdf.namespaceBuilder toString ())
      ))
    val str   = context.noSpaces
    val model = ModelFactory.createDefaultModel()
    RDFDataMgr.read(model, new ByteArrayInputStream(str.getBytes), Lang.JSONLD)

    val graph         = model.getGraph
    val prefixMapping = graph.getPrefixMapping

    "build a PathProp from a path with only one uri with prefix and no hoops" in {
      val path       = "nx:schema"
      val parsedPath = PathParser.parse(path, prefixMapping)
      val result     = fromJena(parsedPath).toTry.success.value
      result shouldEqual UriPath(s"${nxv.namespaceBuilder}schema")
      result.show shouldEqual s"<${nxv.namespaceBuilder}schema>"
    }

    "build a PathProp from a path with only one uri and no hoops" in {
      val path       = s"<${nxv.namespaceBuilder}schema>"
      val parsedPath = PathParser.parse(path, prefixMapping)
      val result     = fromJena(parsedPath).toTry.success.value
      result shouldEqual UriPath(s"${nxv.namespaceBuilder}schema")
      result.show shouldEqual s"<${nxv.namespaceBuilder}schema>"
    }

    "build a PathProp from a follow sequence of paths (3 hoops) with prefixes" in {
      val path       = "nx:schema / nx:schemaGroup ? / nx:name"
      val parsedPath = PathParser.parse(path, prefixMapping)
      val result     = fromJena(parsedPath).toTry.success.value
      result shouldEqual SeqPath(SeqPath(UriPath(s"${nxv.namespaceBuilder}schema"),
                                         PathZeroOrOne(s"${nxv.namespaceBuilder}schemaGroup")),
                                 UriPath(s"${nxv.namespaceBuilder}name"))
      result.show shouldEqual s"<${nxv.namespaceBuilder}schema>/(<${nxv.namespaceBuilder}schemaGroup>)?/<${nxv.namespaceBuilder}name>"
    }

    "build a PathProp from a follow sequence of paths (3 hoops)" in {
      val path =
        s"<${nxv.namespaceBuilder}schema> / <${nxv.namespaceBuilder}schemaGroup> ? / <${nxv.namespaceBuilder}name>*"
      val parsedPath = PathParser.parse(path, prefixMapping)
      val result     = fromJena(parsedPath).toTry.success.value
      result shouldEqual SeqPath(
        SeqPath(UriPath(s"${nxv.namespaceBuilder}schema"), PathZeroOrOne(s"${nxv.namespaceBuilder}schemaGroup")),
        PathZeroOrMore(s"${nxv.namespaceBuilder}name")
      )
      result.show shouldEqual s"<${nxv.namespaceBuilder}schema>/(<${nxv.namespaceBuilder}schemaGroup>)?/(<${nxv.namespaceBuilder}name>)*"
    }

    "build a PathProp from an arbitrary length path" in {
      val path =
        s"<${nxv.namespaceBuilder}schema>+ / <${nxv.namespaceBuilder}schemaGroup> ? / <${nxv.namespaceBuilder}name>"
      val parsedPath = PathParser.parse(path, prefixMapping)
      val result     = fromJena(parsedPath).toTry.success.value
      result shouldEqual SeqPath(SeqPath(PathOneOrMore(s"${nxv.namespaceBuilder}schema"),
                                         PathZeroOrOne(s"${nxv.namespaceBuilder}schemaGroup")),
                                 UriPath(s"${nxv.namespaceBuilder}name"))
      result.show shouldEqual s"(<${nxv.namespaceBuilder}schema>)+/(<${nxv.namespaceBuilder}schemaGroup>)?/<${nxv.namespaceBuilder}name>"
    }

    "build a PathProp which find nodes connected but not by rdf:type (either way round)" in {
      val path       = s"!(rdf:type|^rdf:type|nx:schemaGroup)"
      val parsedPath = PathParser.parse(path, prefixMapping)
      val result     = fromJena(parsedPath).toTry.success.value
      result shouldEqual NegatedSeqPath(
        List(UriPath(s"${rdf.namespaceBuilder}type"),
             InversePath(s"${rdf.namespaceBuilder}type"),
             UriPath(s"${nxv.namespaceBuilder}schemaGroup")))
      result.show shouldEqual s"!(<${rdf.namespaceBuilder}type>|^<${rdf.namespaceBuilder}type>|<${nxv.namespaceBuilder}schemaGroup>)"
    }

    "build a PathProp from a alternate sequence of paths (3 hoops) with prefixes" in {
      val path       = "nx:schema / nx:schemaGroup ? | nx:name"
      val parsedPath = PathParser.parse(path, prefixMapping)
      val result     = fromJena(parsedPath).toTry.success.value
      result shouldEqual AlternativeSeqPath(SeqPath(UriPath(s"${nxv.namespaceBuilder}schema"),
                                                    PathZeroOrOne(s"${nxv.namespaceBuilder}schemaGroup")),
                                            UriPath(s"${nxv.namespaceBuilder}name"))
      result.show shouldEqual s"<${nxv.namespaceBuilder}schema>/(<${nxv.namespaceBuilder}schemaGroup>)?|<${nxv.namespaceBuilder}name>"

    }

    "failed in building a PathProp with an unsupported path property" in {
      val path       = "nx:schema ^ nx:schemaGroup ? | nx:name"
      val parsedPath = PathParser.parse(path, prefixMapping)
      fromJena(parsedPath).toTry.failure.exception shouldBe a[PropPathError]
    }

  }

}

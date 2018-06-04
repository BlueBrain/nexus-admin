package ch.epfl.bluebrain.nexus.admin.core

import ch.epfl.bluebrain.nexus.admin.core.Fault.Unexpected
import ch.epfl.bluebrain.nexus.admin.ld.Const.{nxv, rdf}
import ch.epfl.bluebrain.nexus.admin.refined.ld.{Namespace, Prefix}
import ch.epfl.bluebrain.nexus.admin.refined.organization.OrganizationReference
import ch.epfl.bluebrain.nexus.admin.refined.project.ProjectReference
import ch.epfl.bluebrain.nexus.commons.test.Randomness
import eu.timepit.refined.W
import eu.timepit.refined.api.RefType.{applyRef, refinedRefType}
import eu.timepit.refined.api.Refined
import eu.timepit.refined.auto._
import eu.timepit.refined.numeric.Greater
import io.circe.Json
import org.scalatest.OptionValues

import scala.util.{Failure, Success, Try}

trait TestHelper extends Randomness with OptionValues {

  private val startPool = Vector.range('a', 'z')
  private val pool      = Vector.range('a', 'z') ++ Vector.range('0', '9') :+ '_' :+ '-'

  implicit class EitherSyntax[A](either: Either[String, A]) {
    def toPermTry: Try[A] =
      either.fold(str => Failure(Unexpected(str)), Success(_))
  }
  implicit class JsonSyntax(json: Json) {

    def getString(field: String): String = json.asObject.flatMap(_(field)).flatMap(_.asString).get

    def updateField(field: String, value: String): Json = json.mapObject(_.add(field, Json.fromString(value)))

  }

  def genProjectReference(length: Int = 9): ProjectReference = {

    val idPool = Vector.range('a', 'z') ++ Vector.range('0', '9')
    val id     = s"${genString(length = length, idPool)}/${genString(length = length, idPool)}"
    applyRef[ProjectReference](id).right.get
  }
  def genOrgReference(length: Int = 9): OrganizationReference = {
    val idPool = Vector.range('a', 'z') ++ Vector.range('0', '9')
    val id     = genString(length = length, idPool)
    applyRef[OrganizationReference](id).right.get
  }

  def prefixMapping(namespace: Namespace, prefix: Prefix): Json =
    Json.obj(
      "prefix"    -> Json.fromString(prefix),
      "namespace" -> Json.fromString(namespace)
    )
  def genPrefixMappings(nxvPrefix: Prefix = nxv.prefixBuilder, rdfPrefix: Prefix = rdf.prefixBuilder): Json =
    Json.arr(
      prefixMapping(nxv.namespaceBuilder, nxvPrefix),
      prefixMapping(rdf.namespaceBuilder, rdfPrefix),
    )

  def genProjectValue(nxvPrefix: Prefix = nxv.prefixBuilder, rdfPrefix: Prefix = rdf.prefixBuilder): Json =
    Json.obj(
      "name"           -> Json.fromString(genString()),
      "prefixMappings" -> genPrefixMappings(nxvPrefix, rdfPrefix),
      "base"           -> Json.fromString("https://nexus.example.com/base/")
    )

  def genOrganizationValue(): Json =
    Json.obj(
      "name" -> Json.fromString(genString())
    )

  def genProjectUpdate(): Json =
    genProjectValue()

  def randomProjectPrefix(length: Int Refined Greater[W.`1`.T] = 5): Prefix =
    refinedRefType.unsafeWrap(genString(1, startPool) + genString(genInt(length - 1), pool))

}

object TestHelper extends TestHelper

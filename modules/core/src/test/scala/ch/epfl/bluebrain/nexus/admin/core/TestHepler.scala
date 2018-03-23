package ch.epfl.bluebrain.nexus.admin.core

import ch.epfl.bluebrain.nexus.admin.core.Fault.Unexpected
import ch.epfl.bluebrain.nexus.admin.core.projects.Project.{Config, LoosePrefixMapping, ProjectValue}
import ch.epfl.bluebrain.nexus.admin.ld.Const.{nxv, rdf}
import ch.epfl.bluebrain.nexus.admin.refined.ld.Prefix
import ch.epfl.bluebrain.nexus.admin.refined.project.ProjectReference
import ch.epfl.bluebrain.nexus.commons.test.Randomness
import eu.timepit.refined.W
import eu.timepit.refined.api.RefType.refinedRefType
import eu.timepit.refined.api.Refined
import eu.timepit.refined.auto._
import eu.timepit.refined.numeric.Greater

import scala.util.{Failure, Success, Try}

trait TestHepler extends Randomness {

  private val startPool = Vector.range('a', 'z')
  private val pool      = Vector.range('a', 'z') ++ Vector.range('0', '9') :+ '_' :+ '-'

  implicit class EitherSyntax[A](either: Either[String, A]) {
    def toPermTry: Try[A] =
      either.fold(str => Failure(Unexpected(str)), Success(_))
  }

  def genReference(length: Int = 9): ProjectReference =
    refinedRefType.unsafeWrap(genString(length = length, Vector.range('a', 'z') ++ Vector.range('0', '9')))

  def genPrefixMappings(nxvPrefix: Prefix = nxv.prefixBuilder,
                        rdfPrefix: Prefix = rdf.prefixBuilder): List[LoosePrefixMapping] =
    List(LoosePrefixMapping(nxvPrefix, refinedRefType.unsafeRewrap(nxv.namespaceBuilder)),
         LoosePrefixMapping(rdfPrefix, refinedRefType.unsafeRewrap(rdf.tpe.id)))

  def genProjectValue(nxvPrefix: Prefix = nxv.prefixBuilder, rdfPrefix: Prefix = rdf.prefixBuilder): ProjectValue =
    ProjectValue(genString(), Some(genString()), genPrefixMappings(nxvPrefix, rdfPrefix), Config(genInt().toLong))

  def genProjectUpdate(): ProjectValue = {
    val value = genProjectValue()
    value.copy(prefixMappings = value.prefixMappings ++ genPrefixMappings(randomProjectPrefix(), randomProjectPrefix()))
  }

  def randomProjectPrefix(length: Int Refined Greater[W.`1`.T] = 5): Prefix =
    refinedRefType.unsafeWrap(genString(1, startPool) + genString(genInt(length - 1), pool))

}

object TestHepler extends TestHepler

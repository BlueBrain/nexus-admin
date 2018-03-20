package ch.epfl.bluebrain.nexus.admin.core

import ch.epfl.bluebrain.nexus.admin.core.Fault.Unexpected
import ch.epfl.bluebrain.nexus.admin.core.projects.Project.{Config, LoosePrefixMapping, ProjectValue}
import ch.epfl.bluebrain.nexus.admin.ld.Const.{nxv, rdf}
import ch.epfl.bluebrain.nexus.admin.refined.project.ProjectReference
import ch.epfl.bluebrain.nexus.commons.test.Randomness
import eu.timepit.refined.api.RefType.refinedRefType

import scala.util.{Failure, Success, Try}

trait TestHepler extends Randomness {

  implicit class EitherSyntax[A](either: Either[String, A]) {
    def toPermTry: Try[A] =
      either.fold(str => Failure(Unexpected(str)), Success(_))
  }

  def genReference(length: Int = 9): ProjectReference =
    refinedRefType.unsafeWrap(genString(length = length, Vector.range('a', 'z') ++ Vector.range('0', '9')))

  def genProjectValue(): ProjectValue = {
    val prefixMappings = List(
      LoosePrefixMapping(nxv.prefixBuilder, refinedRefType.unsafeRewrap(nxv.namespaceBuilder)),
      LoosePrefixMapping(rdf.prefixBuilder, refinedRefType.unsafeRewrap(rdf.tpe.id))
    )
    ProjectValue(Some(genString()), Some(genString()), prefixMappings, Config(genInt().toLong))
  }
}

object TestHepler extends TestHepler

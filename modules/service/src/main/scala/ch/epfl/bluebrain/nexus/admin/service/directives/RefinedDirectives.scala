package ch.epfl.bluebrain.nexus.admin.service.directives

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{Directive1, ValidationRejection}
import ch.epfl.bluebrain.nexus.admin.core.CommonRejections.IllegalParam
import eu.timepit.refined.api.{RefType, Validate}

/**
  * Directives specific for refined types resources.
  */
trait RefinedDirectives {

  /**
    * Extracts a refined type from two consecutive path segments.
    */
  def segment2[FTP, F[_, _], P](resolved: ApplyRefDirectivePartiallyApplied[FTP])(
      implicit ev: F[String, P] =:= FTP,
      rt: RefType[F],
      v: Validate[String, P]): Directive1[FTP] = {
    pathPrefix(Segment / Segment).tflatMap {
      case (seg1, seg2) => resolved(s"$seg1/$seg2")
    }
  }

  /**
    * Extracts a segment form the path executes the apply method from the passed ''resolved'' parameter.
    */
  def segment[FTP, F[_, _], P](resolved: ApplyRefDirectivePartiallyApplied[FTP])(
      implicit ev: F[String, P] =:= FTP,
      rt: RefType[F],
      v: Validate[String, P]): Directive1[FTP] =
    pathPrefix(Segment).flatMap(resolved(_))

  /**
    * Defined an apply method which returns a [[Directive1]] of type `T` refined as `FTP` on the right if it
    * satisfies the predicate in `FTP`, or a [[ValidationRejection]] otherwise.
    */
  def of[FTP]: ApplyRefDirectivePartiallyApplied[FTP] =
    new ApplyRefDirectivePartiallyApplied
}

/**
  * Helper class that allows the types `F`, `T`, and `P` to be inferred
  * from calls like `[[RefinedTypeDirectives.of]][F[T, P]](t)`.
  */
final class ApplyRefDirectivePartiallyApplied[FTP] {

  /**
    * Attempts to convert a ''segment'' into a [[Directive1]] of a refined type ''FTP''
    *
    * @param segment the segment to convert to a refined type
    * @tparam F the refined wrapper
    * @tparam P the refined type
    */
  def apply[F[_, _], P](segment: String)(
      implicit ev: F[String, P] =:= FTP,
      rt: RefType[F],
      v: Validate[String, P]
  ): Directive1[FTP] =
    rt.refine[P](segment) match {
      case Right(casted) => provide(ev(casted))
      case Left(err)     => reject(ValidationRejection(err, Some(IllegalParam(err))))
    }
}

object RefinedDirectives extends RefinedDirectives

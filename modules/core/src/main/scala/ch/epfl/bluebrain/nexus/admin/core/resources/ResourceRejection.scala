package ch.epfl.bluebrain.nexus.admin.core.resources

import ch.epfl.bluebrain.nexus.commons.types.Rejection

trait ResourceRejection extends Rejection
object ResourceRejection {
  final case class ShapeConstraintViolations(violations: List[String]) extends ResourceRejection
  final case object ResourceAlreadyExists                              extends ResourceRejection
  final case object UnexpectedCasting                                  extends ResourceRejection
  final case object ResourceDoesNotExists                              extends ResourceRejection
  final case object ParentResourceDoesNotExists                        extends ResourceRejection
  final case object ResourceIsDeprecated                               extends ResourceRejection
  final case object IncorrectRevisionProvided                          extends ResourceRejection
  final case object InvalidParentProvided                              extends ResourceRejection

}

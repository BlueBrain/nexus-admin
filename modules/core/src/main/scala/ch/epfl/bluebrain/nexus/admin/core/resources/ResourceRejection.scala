package ch.epfl.bluebrain.nexus.admin.core.resources

import ch.epfl.bluebrain.nexus.admin.core.CommonRejections
import ch.epfl.bluebrain.nexus.commons.shacl.topquadrant.ValidationReport
import ch.epfl.bluebrain.nexus.commons.types.Rejection

/**
  * Enumeration type for rejections returned when attempting to evaluate commands.
  */
sealed trait ResourceRejection extends Rejection

object ResourceRejection {

  /**
    * Signals a non-specific error during the SHACL validation of a resource payload.
    */
  final case object ResourceValidationError extends ResourceRejection

  /**
    * Signals an error during the SHACL validation of a resource payload.
    *
    * @param report the validation report output
    */
  final case class ResourceValidationFailed(report: ValidationReport) extends ResourceRejection

  /**
    * Signals an error while parsing a JSON-LD payload.
    *
    * @param message human readable error details
    */
  final case class InvalidJsonLD(message: String) extends ResourceRejection

  /**
    * Signals that a resource cannot be created because one with the same identifier already exists.
    */
  final case object ResourceAlreadyExists extends ResourceRejection

  /**
    * Signals that an operation on a resource cannot be performed due to the fact that the referenced resource does not exist.
    */
  final case object ResourceDoesNotExists extends ResourceRejection

  /**
    * Signals that an operation on a resource cannot be performed due to the fact that the referenced parent resource does not exist.
    */
  final case object ParentResourceDoesNotExist extends ResourceRejection

  /**
    * Signals that a resource update cannot be performed due its deprecation status.
    */
  final case object ResourceIsDeprecated extends ResourceRejection

  /**
    * Signals that a resource update cannot be performed due to an incorrect revision provided.
    */
  final case object IncorrectRevisionProvided extends ResourceRejection

  /**
    * Signals any other rejection which gets defined out of this scope
    *
    * @param rejection the underlying rejections that was triggered
    */
  final case class WrappedRejection(rejection: CommonRejections) extends ResourceRejection

}

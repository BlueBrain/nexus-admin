package ch.epfl.bluebrain.nexus.admin.client

import akka.http.scaladsl.model.StatusCode

import scala.reflect.ClassTag

@SuppressWarnings(Array("IncorrectlyNamedExceptions"))
sealed abstract class AdminClientError(val message: String) extends Exception {
  override def fillInStackTrace(): AdminClientError = this
  override val getMessage: String                   = message
}

@SuppressWarnings(Array("IncorrectlyNamedExceptions"))
object AdminClientError {

  final case class UnmarshallingError[A: ClassTag](reason: String)
      extends AdminClientError(
        s"Unable to parse or decode the response from Admin to a '${implicitly[ClassTag[A]]}' due to '$reason'."
      )

  final case class UnknownError(status: StatusCode, entityAsString: String)
      extends AdminClientError("The request did not complete successfully.")
}

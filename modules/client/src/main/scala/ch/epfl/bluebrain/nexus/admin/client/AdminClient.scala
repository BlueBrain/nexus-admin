package ch.epfl.bluebrain.nexus.admin.client

import akka.http.scaladsl.client.RequestBuilding.Get
import akka.http.scaladsl.model.Uri._
import akka.http.scaladsl.model.headers.OAuth2BearerToken
import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.stream.Materializer
import ch.epfl.bluebrain.nexus.admin.client.config.AdminConfig
import ch.epfl.bluebrain.nexus.admin.client.types.Project
import ch.epfl.bluebrain.nexus.admin.refined.project.ProjectReference
import ch.epfl.bluebrain.nexus.commons.http.HttpClient.UntypedHttpClient
import ch.epfl.bluebrain.nexus.commons.http.JsonLdCirceSupport.unmarshaller
import ch.epfl.bluebrain.nexus.commons.http.{HttpClient, UnexpectedUnsuccessfulHttpResponse}
import ch.epfl.bluebrain.nexus.commons.iam.acls.FullAccessControlList
import ch.epfl.bluebrain.nexus.commons.iam.identity.Identity
import ch.epfl.bluebrain.nexus.commons.iam.io.serialization.JsonLdSerialization
import ch.epfl.bluebrain.nexus.commons.types.HttpRejection.UnauthorizedAccess
import io.circe.Decoder
import io.circe.generic.auto._
import journal.Logger

import scala.concurrent.{ExecutionContext, Future}

/**
  * Admin client contract
  *
  * @tparam F the monadic effect type
  */
trait AdminClient[F[_]] {

  /**
    * Retrieves a [[Project]] resource instance.
    *
    * @param name        the project name
    * @param credentials the optionally provided [[OAuth2BearerToken]]
    */
  def getProject(name: ProjectReference, credentials: Option[OAuth2BearerToken]): F[Project]

  /**
    * Retrieves ACLs for a given project resource instance.
    *
    * @param name        the project name
    * @param credentials the optionally provided [[OAuth2BearerToken]]
    */
  def getProjectAcls(name: ProjectReference, credentials: Option[OAuth2BearerToken]): F[FullAccessControlList]

}

object AdminClient {

  private val log = Logger[this.type]

  /**
    * Builds an [[AdminClient]] instance from a provided configuration and implicitly available instances of
    * [[ExecutionContext]], [[Materializer]] and [[UntypedHttpClient]].
    *
    * @param config the provided [[AdminConfig]] instance
    */
  def apply(config: AdminConfig)(implicit
                                 ec: ExecutionContext,
                                 mt: Materializer,
                                 cl: UntypedHttpClient[Future]): AdminClient[Future] = {
    implicit val identityDecoder: Decoder[Identity] = JsonLdSerialization.identityDecoder

    val projectClient = HttpClient.withAkkaUnmarshaller[Project]
    val aclsClient    = HttpClient.withAkkaUnmarshaller[FullAccessControlList]

    new AdminClient[Future] {

      override def getProject(name: ProjectReference, credentials: Option[OAuth2BearerToken]): Future[Project] = {
        val path = Path(name.value)
        projectClient(requestFrom(credentials, path)).recoverWith { case e => recover(e, path) }
      }

      override def getProjectAcls(name: ProjectReference,
                                  credentials: Option[OAuth2BearerToken]): Future[FullAccessControlList] = {
        val path = Path(name.value) + "/acls"
        aclsClient(requestFrom(credentials, path)).recoverWith { case e => recover(e, path) }
      }

      private def requestFrom(credentials: Option[OAuth2BearerToken], path: Path) = {
        val basePath = config.baseUri.path
        val request  = Get(config.baseUri.copy(path = basePath ++ path))
        credentials.map(request.addCredentials).getOrElse(request)
      }

      private def recover(th: Throwable, resource: Path) = th match {
        case UnexpectedUnsuccessfulHttpResponse(HttpResponse(StatusCodes.Unauthorized, _, _, _)) =>
          Future.failed(UnauthorizedAccess)
        case ur: UnexpectedUnsuccessfulHttpResponse =>
          log.warn(
            s"Received an unexpected response status code '${ur.response.status}' from Admin when attempting to perform and operation on a resource '$resource'")
          Future.failed(ur)
        // $COVERAGE-OFF$
        case err =>
          log.error(
            s"Received an unexpected exception from Admin when attempting to perform and operation on a resource '$resource'",
            err)
          Future.failed(err)
        // $COVERAGE-ON$
      }
    }
  }
}

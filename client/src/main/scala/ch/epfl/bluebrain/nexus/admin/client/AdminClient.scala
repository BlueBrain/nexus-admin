package ch.epfl.bluebrain.nexus.admin.client
import akka.http.scaladsl.client.RequestBuilding.Get
import akka.http.scaladsl.model.headers.OAuth2BearerToken
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, StatusCodes}
import akka.stream.ActorMaterializer
import cats.MonadError
import cats.effect.LiftIO
import cats.syntax.applicativeError._
import cats.syntax.functor._
import ch.epfl.bluebrain.nexus.admin.client.config.AdminClientConfig
import ch.epfl.bluebrain.nexus.admin.client.types.{Organization, Project}
import ch.epfl.bluebrain.nexus.commons.http.HttpClient.UntypedHttpClient
import ch.epfl.bluebrain.nexus.commons.http.{HttpClient, UnexpectedUnsuccessfulHttpResponse}
import ch.epfl.bluebrain.nexus.commons.types.HttpRejection.UnauthorizedAccess
import ch.epfl.bluebrain.nexus.iam.client.types.AuthToken
import ch.epfl.bluebrain.nexus.rdf.Iri.Path
import ch.epfl.bluebrain.nexus.rdf.syntax.akka._
import ch.epfl.bluebrain.nexus.rdf.Iri.Path._
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import journal.Logger

import scala.concurrent.ExecutionContext

/**
  * Admin client.
  *
  * @param config configuration for the client
  * @param httpClient HTTP client
  */
class AdminClient[F[_]: LiftIO](config: AdminClientConfig, httpClient: UntypedHttpClient[F])(
    implicit F: MonadError[F, Throwable],
    ex: ExecutionContext,
    mt: ActorMaterializer) {

  private val log           = Logger[this.type]
  private implicit val http = httpClient

  private val projectsClient = HttpClient.withUnmarshaller[F, Project]
  private val orgsClient     = HttpClient.withUnmarshaller[F, Organization]

  /**
    * Fetch [[Project]].
    *
    * @param organization label of the organization to which the project belongs.
    * @param label        project label
    * @param credentials  optional access token
    * @return [[Project]] instance if it exists, [[None]] otherwise, wrapped in [[F]]
    */
  def fetchProject(organization: String, label: String)(implicit credentials: Option[AuthToken]): F[Option[Project]] =
    projectsClient(request("projects" / organization / label))
      .map[Option[Project]](Some(_))
      .recoverWith(recover(s"projects/$organization/$label"))

  /**
    * Fetch [[Organization]].
    *
    * @param label organization label
    * @param credentials optional access token
    * @return [[Organization]] instance if it exists, [[None]] otherwise, wrapped in [[F]]
    */
  def fetchOrganization(label: String)(implicit credentials: Option[AuthToken]): F[Option[Organization]] =
    orgsClient(request("orgs" / label))
      .map[Option[Organization]](Some(_))
      .recoverWith(recover(s"orgs/$label"))

  private def recover[A](path: String): PartialFunction[Throwable, F[Option[A]]] = {
    case UnexpectedUnsuccessfulHttpResponse(HttpResponse(StatusCodes.NotFound, _, _, _)) =>
      F.pure(None)
    case UnexpectedUnsuccessfulHttpResponse(HttpResponse(StatusCodes.Unauthorized, _, _, _)) =>
      F.raiseError(UnauthorizedAccess)
    case ur: UnexpectedUnsuccessfulHttpResponse =>
      log.warn(
        s"Received an unexpected response status code '${ur.response.status}' from Admin when attempting to fetch $path")
      F.raiseError(ur)
    case err =>
      log.error(s"Received an unexpected exception from Admin when attempting to fetch $path", err)
      F.raiseError(err)
  }

  private def request(path: Path)(implicit credentials: Option[AuthToken]): HttpRequest =
    addCredentials(Get((config.baseIri + path).toAkkaUri))

  private def addCredentials(request: HttpRequest)(implicit credentials: Option[AuthToken]): HttpRequest =
    credentials.map(token => request.addCredentials(OAuth2BearerToken(token.value))).getOrElse(request)
}

object AdminClient {

  /**
    * Construct [[AdminClient]].
    *
    * @param config     configuration for the client
    * @param httpClient HTTP client
    * @return new instance of [[AdminClient]]
    */
  def apply[F[_]: LiftIO](config: AdminClientConfig, httpClient: UntypedHttpClient[F])(
      implicit F: MonadError[F, Throwable],
      ex: ExecutionContext,
      mt: ActorMaterializer): AdminClient[F] =
    new AdminClient(config, httpClient)
}

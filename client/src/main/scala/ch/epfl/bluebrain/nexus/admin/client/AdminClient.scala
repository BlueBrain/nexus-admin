package ch.epfl.bluebrain.nexus.admin.client

import akka.actor.ActorSystem
import akka.http.scaladsl.client.RequestBuilding.Get
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.OAuth2BearerToken
import akka.http.scaladsl.unmarshalling.FromEntityUnmarshaller
import akka.stream.{ActorMaterializer, Materializer}
import cats.MonadError
import cats.effect.{IO, LiftIO}
import cats.implicits._
import ch.epfl.bluebrain.nexus.admin.client.AdminClientError.{UnknownError, UnmarshallingError}
import ch.epfl.bluebrain.nexus.admin.client.config.AdminClientConfig
import ch.epfl.bluebrain.nexus.admin.client.types.{Organization, Project}
import ch.epfl.bluebrain.nexus.commons.http.HttpClient
import ch.epfl.bluebrain.nexus.commons.http.HttpClient.UntypedHttpClient
import ch.epfl.bluebrain.nexus.commons.http.JsonLdCirceSupport._
import ch.epfl.bluebrain.nexus.iam.client.IamClientError.{Forbidden, Unauthorized}
import ch.epfl.bluebrain.nexus.iam.client.types.AuthToken
import ch.epfl.bluebrain.nexus.rdf.Iri.Path
import ch.epfl.bluebrain.nexus.rdf.Iri.Path._
import ch.epfl.bluebrain.nexus.rdf.syntax.akka._
import io.circe.{DecodingFailure, ParsingFailure}
import journal.Logger

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}
import scala.reflect.ClassTag

/**
  * Admin client.
  *
  * @param cfg configuration for the client
  */
class AdminClient[F[_]](cfg: AdminClientConfig)(
    implicit
    F: MonadError[F, Throwable],
    pc: HttpClient[F, Project],
    oc: HttpClient[F, Organization],
) {

  /**
    * Fetch [[Project]].
    *
    * @param organization label of the organization to which the project belongs.
    * @param label        project label
    * @param credentials  optional access token
    * @return [[Project]] instance if it exists, [[None]] otherwise, wrapped in [[F]]
    */
  def fetchProject(organization: String, label: String)(implicit credentials: Option[AuthToken]): F[Option[Project]] =
    pc(request("projects" / organization / label))
      .map[Option[Project]](Some(_))
      .recoverWith {
        case UnknownError(StatusCodes.NotFound, _) => F.pure(None)
      }

  /**
    * Fetch [[Organization]].
    *
    * @param label organization label
    * @param credentials optional access token
    * @return [[Organization]] instance if it exists, [[None]] otherwise, wrapped in [[F]]
    */
  def fetchOrganization(label: String)(implicit credentials: Option[AuthToken]): F[Option[Organization]] =
    oc(request("orgs" / label))
      .map[Option[Organization]](Some(_))
      .recoverWith {
        case UnknownError(StatusCodes.NotFound, _) => F.pure(None)
      }

  private def request(path: Path)(implicit credentials: Option[AuthToken]): HttpRequest =
    addCredentials(Get((cfg.baseUri + path).toAkkaUri))

  private def addCredentials(request: HttpRequest)(implicit credentials: Option[AuthToken]): HttpRequest =
    credentials.map(token => request.addCredentials(OAuth2BearerToken(token.value))).getOrElse(request)
}

object AdminClient {

  private def httpClient[F[_], A: ClassTag](
      implicit L: LiftIO[F],
      F: MonadError[F, Throwable],
      ec: ExecutionContext,
      mt: Materializer,
      cl: UntypedHttpClient[F],
      um: FromEntityUnmarshaller[A]
  ): HttpClient[F, A] = new HttpClient[F, A] {
    private val logger = Logger(s"AdminHttpClient[${implicitly[ClassTag[A]]}]")

    override def apply(req: HttpRequest): F[A] =
      cl.apply(req).flatMap { resp =>
        resp.status match {
          case StatusCodes.Unauthorized =>
            cl.toString(resp.entity).flatMap { entityAsString =>
              F.raiseError[A](Unauthorized(entityAsString))
            }
          case StatusCodes.Forbidden =>
            logger.error(s"Received Forbidden when accessing '${req.method.name()} ${req.uri.toString()}'.")
            cl.toString(resp.entity).flatMap { entityAsString =>
              F.raiseError[A](Forbidden(entityAsString))
            }
          case other if other.isSuccess() =>
            val value = L.liftIO(IO.fromFuture(IO(um(resp.entity))))
            value.recoverWith {
              case pf: ParsingFailure =>
                logger.error(
                  s"Failed to parse a successful response of '${req.method.name()} ${req.getUri().toString}'.")
                F.raiseError[A](UnmarshallingError(pf.getMessage()))
              case df: DecodingFailure =>
                logger.error(
                  s"Failed to decode a successful response of '${req.method.name()} ${req.getUri().toString}'.")
                F.raiseError(UnmarshallingError(df.getMessage()))
            }
          case other =>
            cl.toString(resp.entity).flatMap { entityAsString =>
              logger.error(
                s"Received '${other.value}' when accessing '${req.method.name()} ${req.uri.toString()}', response entity as string: '$entityAsString.'")
              F.raiseError[A](UnknownError(other, entityAsString))
            }
        }
      }

    override def discardBytes(entity: HttpEntity): F[HttpMessage.DiscardedEntity] =
      cl.discardBytes(entity)

    override def toString(entity: HttpEntity): F[String] =
      cl.toString(entity)
  }

  /**
    * Construct [[AdminClient]].
    *
    * @param cfg configuration for the client
    * @return new instance of [[AdminClient]]
    */
  def apply[F[_]: LiftIO](cfg: AdminClientConfig)(implicit F: MonadError[F, Throwable],
                                                  as: ActorSystem): AdminClient[F] = {
    implicit val mt: ActorMaterializer        = ActorMaterializer()
    implicit val ec: ExecutionContextExecutor = as.dispatcher
    implicit val ucl: UntypedHttpClient[F]    = HttpClient.untyped[F]

    implicit val pc: HttpClient[F, Project]      = httpClient[F, Project]
    implicit val oc: HttpClient[F, Organization] = httpClient[F, Organization]
    new AdminClient(cfg)
  }
}

package ch.epfl.bluebrain.nexus.admin.client

import akka.actor.ActorSystem
import akka.http.scaladsl.client.RequestBuilding.Get
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.OAuth2BearerToken
import akka.http.scaladsl.unmarshalling.FromEntityUnmarshaller
import akka.stream.scaladsl.Sink
import akka.stream.{ActorMaterializer, Materializer}
import cats.effect.{Effect, IO, LiftIO}
import cats.implicits._
import ch.epfl.bluebrain.nexus.admin.client.AdminClientError.{UnknownError, UnmarshallingError}
import ch.epfl.bluebrain.nexus.admin.client.config.AdminClientConfig
import ch.epfl.bluebrain.nexus.admin.client.types.events.{Event, OrganizationEvent, ProjectEvent}
import ch.epfl.bluebrain.nexus.admin.client.types.{Organization, Project}
import ch.epfl.bluebrain.nexus.commons.http.HttpClient
import ch.epfl.bluebrain.nexus.commons.http.HttpClient.UntypedHttpClient
import ch.epfl.bluebrain.nexus.commons.http.JsonLdCirceSupport._
import ch.epfl.bluebrain.nexus.iam.client.IamClientError.{Forbidden, Unauthorized}
import ch.epfl.bluebrain.nexus.iam.client.types.AuthToken
import ch.epfl.bluebrain.nexus.rdf.Iri.AbsoluteIri
import ch.epfl.bluebrain.nexus.rdf.syntax.akka._
import io.circe.{DecodingFailure, ParsingFailure}
import journal.Logger

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}
import scala.reflect.ClassTag

/**
  * Admin client.
  *
  * @param cfg configuration for the client
  */
class AdminClient[F[_]] private[client] (source: EventSource[Event], cfg: AdminClientConfig)(
    implicit
    F: Effect[F],
    mt: Materializer,
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
    pc(request(cfg.projectsIri + organization + label))
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
    oc(request(cfg.orgsIri + label))
      .map[Option[Organization]](Some(_))
      .recoverWith {
        case UnknownError(StatusCodes.NotFound, _) => F.pure(None)
      }

  /**
    * It applies the provided function ''f'' to the Project Server-sent events (SSE)
    *
    * @param f      the function that gets executed when a new [[ProjectEvent]] appears
    * @param offset the optional offset from where to start streaming the events
    */
  def projectEvents(f: ProjectEvent => F[Unit], offset: Option[String] = None)(
      implicit cred: Option[AuthToken]): Unit = {
    val pf: PartialFunction[Event, F[Unit]] = { case ev: ProjectEvent => f(ev) }
    events(cfg.projectsIri + "events", pf, offset)
  }

  /**
    * It applies the provided function ''f'' to the Organization Server-sent events (SSE)
    *
    * @param f      the function that gets executed when a new [[OrganizationEvent]] appears
    * @param offset the optional offset from where to start streaming the events
    */
  def organizationEvents(f: OrganizationEvent => F[Unit], offset: Option[String] = None)(
      implicit cred: Option[AuthToken]): Unit = {
    val pf: PartialFunction[Event, F[Unit]] = { case ev: OrganizationEvent => f(ev) }
    events(cfg.orgsIri + "events", pf, offset)
  }

  /**
    * It applies the provided function ''f'' to the Server-sent events (SSE)
    *
    * @param f      the function that gets executed when a new [[Event]] appears
    * @param offset the optional offset from where to start streaming the events
    */
  def events(f: Event => F[Unit], offset: Option[String] = None)(implicit cred: Option[AuthToken]): Unit = {
    val pf: PartialFunction[Event, F[Unit]] = { case ev: Event => f(ev) }
    events(cfg.internalIri + "events", pf, offset)
  }

  private def events(iri: AbsoluteIri, f: PartialFunction[Event, F[Unit]], offset: Option[String])(
      implicit cred: Option[AuthToken]): Unit =
    source(iri, offset)
      .mapAsync(1) { event =>
        f.lift(event) match {
          case Some(evaluated) => F.toIO(evaluated).unsafeToFuture()
          case _               => Future.unit
        }
      }
      .to(Sink.ignore)
      .mapMaterializedValue(_ => ())
      .run()

  private def request(iri: AbsoluteIri)(implicit credentials: Option[AuthToken]): HttpRequest =
    addCredentials(Get(iri.toAkkaUri))

  private def addCredentials(request: HttpRequest)(implicit credentials: Option[AuthToken]): HttpRequest =
    credentials.map(token => request.addCredentials(OAuth2BearerToken(token.value))).getOrElse(request)
}

object AdminClient {

  private def httpClient[F[_], A: ClassTag](
      implicit L: LiftIO[F],
      F: Effect[F],
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
  def apply[F[_]: Effect](cfg: AdminClientConfig)(implicit as: ActorSystem): AdminClient[F] = {
    implicit val mt: ActorMaterializer        = ActorMaterializer()
    implicit val ec: ExecutionContextExecutor = as.dispatcher
    implicit val ucl: UntypedHttpClient[F]    = HttpClient.untyped[F]

    implicit val pc: HttpClient[F, Project]      = httpClient[F, Project]
    implicit val oc: HttpClient[F, Organization] = httpClient[F, Organization]
    val sse: EventSource[Event]                  = EventSource[Event](cfg)
    new AdminClient(sse, cfg)
  }
}

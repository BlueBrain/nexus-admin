package ch.epfl.bluebrain.nexus.admin.client

import akka.http.scaladsl.client.RequestBuilding.Get
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model.headers.OAuth2BearerToken
import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.stream.Materializer
import ch.epfl.bluebrain.nexus.admin.client.config.AdminConfig
import ch.epfl.bluebrain.nexus.admin.client.types.{Account, Project}
import ch.epfl.bluebrain.nexus.commons.http.HttpClient.UntypedHttpClient
import ch.epfl.bluebrain.nexus.commons.http.JsonLdCirceSupport.unmarshaller
import ch.epfl.bluebrain.nexus.commons.http.{HttpClient, UnexpectedUnsuccessfulHttpResponse}
import ch.epfl.bluebrain.nexus.commons.types.HttpRejection.UnauthorizedAccess
import ch.epfl.bluebrain.nexus.iam.client.types.Address._
import ch.epfl.bluebrain.nexus.iam.client.types.{Address, AuthToken, FullAccessControlList}
import io.circe.generic.auto._
import journal.Logger
import monix.eval.Task

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
    * @param account     the organization label
    * @param project     the project label
    * @param credentials the optionally provided [[AuthToken]]
    */
  def getProject(account: String, project: String)(implicit credentials: Option[AuthToken]): F[Option[Project]]

  /**
    * Retrieves a [[Account]] resource instance.
    *
    * @param name        the organization name
    * @param credentials the optionally provided [[AuthToken]]
    */
  def getAccount(name: String)(implicit credentials: Option[AuthToken]): F[Option[Account]]

  /**
    * Retrieves ACLs for a given project resource instance.
    *
    * @param account     the organization label
    * @param project     the project label
    * @param parents     matches only the provided project ''path'' (false) or the parents also (true)
    * @param self        matches only the caller ''identities'' (false) or any identity which has the right own access (true)
    * @param credentials the optionally provided [[AuthToken]]
    */
  def getProjectAcls(account: String, project: String, parents: Boolean = false, self: Boolean = false)(
      implicit credentials: Option[AuthToken]): F[Option[FullAccessControlList]]

}

object AdminClient {

  private val log = Logger[this.type]

  /**
    * Builds an [[AdminClient]] instance for the effect type [[Future]] from a provided configuration and implicitly available instances of
    * [[ExecutionContext]], [[Materializer]] and [[UntypedHttpClient]].
    *
    * @param config the provided [[AdminConfig]] instance
    */
  def future(config: AdminConfig)(implicit
                                  ec: ExecutionContext,
                                  mt: Materializer,
                                  cl: UntypedHttpClient[Future]): AdminClient[Future] = {
    val projectClient = HttpClient.withAkkaUnmarshaller[Project]
    val accountClient = HttpClient.withAkkaUnmarshaller[Account]
    val aclsClient    = HttpClient.withAkkaUnmarshaller[FullAccessControlList]

    new AdminClient[Future] {

      override def getProject(account: String, project: String)(
          implicit credentials: Option[AuthToken]): Future[Option[Project]] = {
        val path = "projects" / account / project
        projectClient(requestFrom(path)).map(Some.apply).recoverWith { case e => recover(e, path) }
      }

      override def getAccount(name: String)(implicit credentials: Option[AuthToken]): Future[Option[Account]] = {
        val path = "orgs" / name
        accountClient(requestFrom(path)).map(Some.apply).recoverWith { case e => recover(e, path) }
      }

      override def getProjectAcls(account: String, project: String, parents: Boolean = false, self: Boolean = false)(
          implicit credentials: Option[AuthToken]): Future[Option[FullAccessControlList]] = {
        val path  = "projects" / account / project / "acls"
        val query = Query("parents" -> parents.toString, "self" -> self.toString)
        aclsClient(requestFrom(path, Some(query))).map(Some.apply).recoverWith { case e => recover(e, path) }
      }

      private def requestFrom(path: Address, query: Option[Query] = None)(implicit credentials: Option[AuthToken]) = {
        val request = query match {
          case None    => Get(config.baseUri.append(path))
          case Some(q) => Get(config.baseUri.append(path).withQuery(q))
        }
        credentials.map(request.addCredentials(_)).getOrElse(request)
      }

      private def recover[A](th: Throwable, resource: Address): Future[Option[A]] = th match {
        case UnexpectedUnsuccessfulHttpResponse(HttpResponse(StatusCodes.Unauthorized, _, _, _)) =>
          Future.failed(UnauthorizedAccess)
        case ur: UnexpectedUnsuccessfulHttpResponse if ur.response.status == StatusCodes.NotFound =>
          log.info(s"Resource '$resource' not found")
          Future.successful(None)
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

  /**
    * Builds an [[AdminClient]] instance for the effect type [[Task]] from a provided configuration and implicitly available instances of
    * [[ExecutionContext]], [[Materializer]] and [[UntypedHttpClient]].
    *
    * @param config the provided [[AdminConfig]]
    */
  def task(config: AdminConfig)(implicit
                                ec: ExecutionContext,
                                mt: Materializer,
                                cl: UntypedHttpClient[Future]): AdminClient[Task] = {
    val underlying = future(config)
    new AdminClient[Task] {

      override def getProject(account: String, project: String)(
          implicit credentials: Option[AuthToken]): Task[Option[Project]] =
        Task.deferFuture(underlying.getProject(account, project))

      override def getAccount(name: String)(implicit credentials: Option[AuthToken]): Task[Option[Account]] =
        Task.deferFuture(underlying.getAccount(name))

      override def getProjectAcls(account: String, project: String, parents: Boolean, self: Boolean)(
          implicit credentials: Option[AuthToken]): Task[Option[FullAccessControlList]] =
        Task.deferFuture(underlying.getProjectAcls(account, project, parents, self))
    }

  }
  private implicit def toAkka(token: AuthToken): OAuth2BearerToken = OAuth2BearerToken(token.value)

}

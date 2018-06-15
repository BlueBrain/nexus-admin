package ch.epfl.bluebrain.nexus.admin.client

import akka.http.scaladsl.client.RequestBuilding.Get
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model.headers.OAuth2BearerToken
import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.stream.Materializer
import ch.epfl.bluebrain.nexus.admin.client.config.AdminConfig
import ch.epfl.bluebrain.nexus.admin.client.types.Project
import ch.epfl.bluebrain.nexus.admin.refined.project.ProjectReference
import ch.epfl.bluebrain.nexus.commons.http.HttpClient.UntypedHttpClient
import ch.epfl.bluebrain.nexus.commons.http.JsonLdCirceSupport.unmarshaller
import ch.epfl.bluebrain.nexus.commons.http.{HttpClient, UnexpectedUnsuccessfulHttpResponse}
import ch.epfl.bluebrain.nexus.commons.types.HttpRejection.UnauthorizedAccess
import ch.epfl.bluebrain.nexus.iam.client.types.Address._
import ch.epfl.bluebrain.nexus.iam.client.types.{Address, AuthToken, FullAccessControlList}
import eu.timepit.refined.auto._
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
    * @param credentials the optionally provided [[AuthToken]]
    */
  def getProject(name: ProjectReference)(implicit credentials: Option[AuthToken]): F[Project]

  /**
    * Retrieves ACLs for a given project resource instance.
    *
    * @param name        the project name
    * @param parents     matches only the provided project ''path'' (false) or the parents also (true)
    * @param self        matches only the caller ''identities'' (false) or any identity which has the right own access (true)
    * @param credentials the optionally provided [[AuthToken]]
    */
  def getProjectAcls(name: ProjectReference, parents: Boolean = false, self: Boolean = false)(
      implicit credentials: Option[AuthToken]): F[FullAccessControlList]

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
    val projectClient = HttpClient.withAkkaUnmarshaller[Project]
    val aclsClient    = HttpClient.withAkkaUnmarshaller[FullAccessControlList]

    new AdminClient[Future] {

      override def getProject(name: ProjectReference)(implicit credentials: Option[AuthToken]): Future[Project] = {
        val path = name.organizationReference.value / name.projectLabel
        projectClient(requestFrom(path)).recoverWith { case e => recover(e, path) }
      }

      override def getProjectAcls(name: ProjectReference, parents: Boolean = false, self: Boolean = false)(
          implicit credentials: Option[AuthToken]): Future[FullAccessControlList] = {
        val path  = name.organizationReference.value / name.projectLabel / "acls"
        val query = Query("parents" -> parents.toString, "self" -> self.toString)
        aclsClient(requestFrom(path, Some(query))).recoverWith { case e => recover(e, path) }
      }

      private def requestFrom(path: Address, query: Option[Query] = None)(implicit credentials: Option[AuthToken]) = {
        val request = query match {
          case None    => Get(config.baseUri.append(path))
          case Some(q) => Get(config.baseUri.append(path).withQuery(q))
        }
        credentials.map(request.addCredentials(_)).getOrElse(request)
      }

      private def recover(th: Throwable, resource: Address) = th match {
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

  private implicit def toAkka(token: AuthToken): OAuth2BearerToken = OAuth2BearerToken(token.value)

}

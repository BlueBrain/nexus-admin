package ch.epfl.bluebrain.nexus.admin.service.directives

import akka.http.javadsl.server.CustomRejection
import akka.http.scaladsl.model.headers.OAuth2BearerToken
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.directives.FutureDirectives.onComplete
import akka.http.scaladsl.server.directives.RouteDirectives.reject
import akka.http.scaladsl.server.{AuthorizationFailedRejection, _}
import ch.epfl.bluebrain.nexus.admin.service.directives.AuthDirectives._
import ch.epfl.bluebrain.nexus.admin.service.rejections.CommonRejections
import ch.epfl.bluebrain.nexus.admin.service.rejections.CommonRejections.DownstreamServiceError
import ch.epfl.bluebrain.nexus.commons.iam.IamClient
import ch.epfl.bluebrain.nexus.commons.iam.acls.{Path, Permissions}
import ch.epfl.bluebrain.nexus.commons.iam.identity.Caller
import ch.epfl.bluebrain.nexus.commons.types.HttpRejection.UnauthorizedAccess
import eu.timepit.refined.api.{RefType, Validate}

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

trait AuthDirectives {

  /**
    * Construct a [[ApplyRefAuthPartiallyApplied]] with a method apply to check if the caller has permissions on a provided ''resource'' constrained by the refined type ''FTP''
    *
    * @tparam FTP the restricted subset of [[Permissions]]
    */
  def authorizeOn[FTP]: ApplyRefAuthPartiallyApplied[FTP] = new ApplyRefAuthPartiallyApplied

  /**
    * Authenticates the requested with the provided ''cred'' and returns the ''caller''
    *
    * @return the [[Caller]]
    */
  def authCaller(implicit iamClient: IamClient[Future], cred: Option[OAuth2BearerToken]): Directive1[Caller] =
    onComplete(iamClient.getCaller(cred, filterGroups = true)).flatMap {
      case Success(caller)             => provide(caller)
      case Failure(UnauthorizedAccess) => reject(AuthorizationFailedRejection)
      case Failure(err)                => reject(authorizationRejection(err))
    }

}

/**
  * Helper class that allows the types `F`, `T`, and `P` to be inferred
  * from calls like `[[AuthDirectives.onPath]][F[T, P]](t)`.
  */
final class ApplyRefAuthPartiallyApplied[FTP] {

  /**
    * Checks if the caller associated with ''cred'' has permissions constrained by the type ''FTP'' on the path ''resource''.
    * Attempts to convert a ''resource'' into a [[Directive1]] of a refined type ''FTP''
    *
    * @param resource the segment path to convert to a refined type
    * @tparam F the refined wrapper
    * @tparam P the refined type
    */
  def apply[F[_, _], P](resource: Path)(
      implicit ev: F[Permissions, P] =:= FTP,
      rt: RefType[F],
      v: Validate[Permissions, P],
      client: IamClient[Future],
      cred: Option[OAuth2BearerToken]
  ): Directive1[FTP] =
    onComplete(client.getAcls(resource, self = true, parents = true)).flatMap {
      case Success(acl) =>
        rt.refine[P](acl.permissions) match {
          case Right(casted) => provide(ev(casted))
          case Left(_)       => reject(AuthorizationFailedRejection)
        }
      case Failure(UnauthorizedAccess) => reject(AuthorizationFailedRejection)
      case Failure(err)                => reject(authorizationRejection(err))
    }

  /**
    * Checks if the caller associated with ''cred'' has permissions constrained by the type ''FTP'' on the path ''resource''.
    * Attempts to convert a ''resource'' into a [[Directive1]] of a refined type ''FTP''
    *
    * @param resource the segment string to convert to a refined type
    * @tparam F the refined wrapper
    * @tparam P the refined type
    */
  def apply[F[_, _], P](resource: String)(
      implicit ev: F[Permissions, P] =:= FTP,
      rt: RefType[F],
      v: Validate[Permissions, P],
      client: IamClient[Future],
      cred: Option[OAuth2BearerToken]
  ): Directive1[FTP] = apply(Path(resource))

}
object AuthDirectives extends AuthDirectives {

  /**
    * Signals that the authentication was rejected with an unexpected error.
    *
    * @param err the [[service.CommonRejections]]
    */
  final case class CustomAuthRejection(err: CommonRejections) extends CustomRejection

  private[directives] def authorizationRejection(err: Throwable) =
    CustomAuthRejection(
      DownstreamServiceError(Try(err.getMessage).getOrElse("error while authenticating on the downstream service")))
}

package ch.epfl.bluebrain.nexus.admin.core.directives

import akka.http.scaladsl.model.headers.OAuth2BearerToken
import akka.http.scaladsl.server.Directive1
import akka.http.scaladsl.server.Directives._
import cats.MonadError
import cats.instances.future._
import cats.syntax.all._
import ch.epfl.bluebrain.nexus.admin.core.rejections.CommonRejections.DownstreamServiceError
import ch.epfl.bluebrain.nexus.commons.iam.IamClient
import ch.epfl.bluebrain.nexus.commons.iam.acls.{Path, Permissions}
import ch.epfl.bluebrain.nexus.commons.types.HttpRejection.UnauthorizedAccess
import eu.timepit.refined.api.RefType.applyRef
import eu.timepit.refined.api.{Refined, Validate}

import scala.concurrent.{ExecutionContext, Future}

/**
  * Provides the directives to authorize a ''caller''
  *
  * @param F a MonadError typeclass instance for ''F[_]''
  * @tparam F the monadic effect type
  */
class AuthorizeDirective[F[_]](implicit F: MonadError[F, Throwable]) {

  /**
    * Checks if the caller associated with ''cred'' has permissions constrained by the type ''Perm'' on the path ''resource''.
    *
    * @param resource the path which the caller wants to access
    * @tparam A the restricted subset of [[Permissions]]
    */
  def apply[A](resource: Path)(implicit client: IamClient[F],
                               cred: Option[OAuth2BearerToken],
                               V: Validate[Permissions, A]): Directive1[F[Permissions Refined A]] =
    provide {
      client
        .getAcls(resource, self = true, parents = true)
        .flatMap[Permissions Refined A](acl => {
          applyRef[Permissions Refined A](acl.permissions) match {
            case Right(instance) => F.pure(instance)
            case Left(_)         => F.raiseError(UnauthorizedAccess)
          }
        })
        .recoverWith {
          case UnauthorizedAccess => F.raiseError(UnauthorizedAccess)
          case other              => F.raiseError(DownstreamServiceError(other.getMessage))
        }

    }

  /**
    * Checks if the caller associated with ''cred'' has permissions constrained by the type ''Perm'' on the path ''resource''.
    *
    * @param resource the resource id which the caller wants to access
    * @tparam A the restricted subset of [[Permissions]]
    */
  def apply[A](resource: String)(implicit client: IamClient[F],
                                 cred: Option[OAuth2BearerToken],
                                 V: Validate[Permissions, A]): Directive1[F[Permissions Refined A]] =
    apply[A](Path(resource))

}

object AuthorizeDirective {

  /**
    * Construct an [[AuthorizeDirective]] of [[Future]]
    *
    * @param ec the implicitly available [[ExecutionContext]]
    */
  def fromFuture(implicit ec: ExecutionContext): AuthorizeDirective[Future] =
    new AuthorizeDirective[Future]
}

package ch.epfl.bluebrain.nexus.admin.marshallers

import akka.http.scaladsl.marshalling.GenericMarshallers.eitherMarshaller
import akka.http.scaladsl.marshalling._
import akka.http.scaladsl.model.MediaTypes.`application/json`
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model._
import ch.epfl.bluebrain.nexus.admin.CommonRejection
import ch.epfl.bluebrain.nexus.admin.config.AppConfig.orderedKeys
import ch.epfl.bluebrain.nexus.admin.config.Contexts._
import ch.epfl.bluebrain.nexus.admin.organizations.OrganizationRejection
import ch.epfl.bluebrain.nexus.admin.projects.ProjectRejection
import ch.epfl.bluebrain.nexus.commons.http.JsonLdCirceSupport.OrderedKeys
import ch.epfl.bluebrain.nexus.commons.http.RdfMediaTypes._
import ch.epfl.bluebrain.nexus.commons.http.syntax.circe._
import ch.epfl.bluebrain.nexus.commons.types.{HttpRejection, Rejection}
import ch.epfl.bluebrain.nexus.rdf.syntax.circe.context._
import ch.epfl.bluebrain.nexus.service.http.directives.StatusFrom
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe._
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.semiauto.deriveEncoder

import scala.collection.immutable.Seq

object instances extends FailFastCirceSupport {

  override def unmarshallerContentTypes: Seq[ContentTypeRange] =
    List(`application/json`, `application/ld+json`, `application/sparql-results+json`)

  /**
    * `Json` => HTTP entity
    *
    * @return marshaller for JSON-LD value
    */
  implicit def jsonLd(implicit printer: Printer = Printer.noSpaces.copy(dropNullValues = true),
                      keys: OrderedKeys = orderedKeys): ToEntityMarshaller[Json] = {
    val marshallers = Seq(`application/ld+json`, `application/json`).map(contentType =>
      Marshaller.withFixedContentType[Json, MessageEntity](contentType) { json =>
        HttpEntity(`application/ld+json`, printer.pretty(json.sortKeys))
    })
    Marshaller.oneOf(marshallers: _*)
  }

  /**
    * `A` => HTTP entity
    *
    * @tparam A type to encode
    * @return marshaller for any `A` value
    */
  implicit def httpEntity[A](implicit encoder: Encoder[A],
                             printer: Printer = Printer.noSpaces.copy(dropNullValues = true),
                             keys: OrderedKeys = orderedKeys): ToEntityMarshaller[A] =
    jsonLd.compose(encoder.apply)

  /**
    * `Rejection` => HTTP entity
    *
    * @return marshaller for Rejection value
    */
  implicit def rejectionEntity(implicit
                               printer: Printer = Printer.noSpaces.copy(dropNullValues = true),
                               keys: OrderedKeys = orderedKeys): ToEntityMarshaller[Rejection] = {
    val marshallers = Seq(`application/ld+json`, `application/json`).map(contentType =>
      Marshaller.withFixedContentType[Rejection, MessageEntity](contentType) { rejection =>
        HttpEntity(`application/ld+json`, printer.pretty(rejectionEncoder(rejection).sortKeys))
    })
    Marshaller.oneOf(marshallers: _*)
  }

  /**
    * `Rejection` => HTTP response
    *
    * @return marshaller for Rejection value
    */
  implicit def rejectionResponse(implicit printer: Printer = Printer.noSpaces.copy(dropNullValues = true),
                                 ordered: OrderedKeys = orderedKeys): ToResponseMarshaller[Rejection] = {
    val marshallers = Seq(`application/ld+json`, `application/json`).map { contentType =>
      Marshaller.withFixedContentType[Rejection, HttpResponse](contentType) { rejection =>
        HttpResponse(status = statusCodeFrom(rejection),
                     entity = HttpEntity(contentType, printer.pretty(rejectionEncoder(rejection).sortKeys)))
      }
    }
    Marshaller.oneOf(marshallers: _*)
  }

  /**
    * `Either[Rejection,A]` => HTTP entity
    *
    * @tparam A type to encode
    * @return marshaller for any `A` value
    */
  implicit def either[A: Encoder](
      implicit
      printer: Printer = Printer.noSpaces.copy(dropNullValues = true)): ToResponseMarshaller[Either[Rejection, A]] =
    eitherMarshaller(rejectionResponse, httpEntity[A])

  private implicit val rejectionConfig: Configuration = Configuration.default.withDiscriminator("code")

  private val organizationRejectionEncoder: Encoder[OrganizationRejection] =
    deriveEncoder[OrganizationRejection].mapJson(_ addContext errorCtxUri)

  private val projectRejectionEncoder: Encoder[ProjectRejection] =
    deriveEncoder[ProjectRejection].mapJson(_ addContext errorCtxUri)

  private val commonRejectionEncoder: Encoder[CommonRejection] =
    deriveEncoder[CommonRejection].mapJson(_ addContext errorCtxUri)

  private val httpRejectionEncoder: Encoder[HttpRejection] =
    deriveEncoder[HttpRejection].mapJson(_ addContext errorCtxUri)

  private val rejectionEncoder: Encoder[Rejection] = Encoder.encodeJson.contramap {
    case r: OrganizationRejection => organizationRejectionEncoder(r)
    case r: ProjectRejection      => projectRejectionEncoder(r)
    case r: CommonRejection       => commonRejectionEncoder(r)
    case r: HttpRejection         => httpRejectionEncoder(r)
  }

  private val projectStatusCodeFrom: StatusFrom[ProjectRejection] = StatusFrom {
    case _: ProjectRejection.IncorrectRev          => Conflict
    case ProjectRejection.ProjectExists            => Conflict
    case ProjectRejection.ProjectNotFound          => NotFound
    case ProjectRejection.OrganizationNotFound     => NotFound
    case ProjectRejection.ProjectIsDeprecated      => BadRequest
    case ProjectRejection.OrganizationIsDeprecated => BadRequest
    case _: ProjectRejection.InvalidProjectFormat  => BadRequest
  }

  private val organizationStatusCodeFrom: StatusFrom[OrganizationRejection] = StatusFrom {
    case _: OrganizationRejection.IncorrectRev              => Conflict
    case OrganizationRejection.OrganizationExists           => Conflict
    case OrganizationRejection.OrganizationNotFound         => NotFound
    case _: OrganizationRejection.InvalidOrganizationFormat => BadRequest
  }

  private val statusCodeFrom: StatusFrom[Rejection] = StatusFrom {
    case r: ProjectRejection                       => projectStatusCodeFrom(r)
    case r: OrganizationRejection                  => organizationStatusCodeFrom(r)
    case _: HttpRejection                          => BadRequest
    case _: CommonRejection.DownstreamServiceError => InternalServerError
    case _                                         => BadRequest
  }

}

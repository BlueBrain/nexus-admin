package ch.epfl.bluebrain.nexus.admin.directives

import akka.http.scaladsl.unmarshalling.{FromStringUnmarshaller, Unmarshaller}
import ch.epfl.bluebrain.nexus.commons.types.HttpRejection.WrongOrInvalidJson
import io.circe.parser._
import io.circe.{Decoder, Json}

import scala.util.Try

object StringUnmarshaller {

  /**
    * String => Json array => `A`
    *
    * @return unmarshaller for `A`
    */
  def unmarshallJsonArr[A: Decoder]: FromStringUnmarshaller[A] = unmarshaller { value =>
    Right(Json.arr(value.split(",").foldLeft(Vector.empty[Json])((acc, c) => acc :+ Json.fromString(c)): _*))
  }

  /**
    * String => Json string => `A`
    *
    * @return unmarshaller for `A`
    */
  def unmarshallJsonString[A: Decoder]: FromStringUnmarshaller[A] = unmarshaller { value =>
    Right(Json.fromString(value))
  }

  /**
    * String => Json => `A`
    *
    * @return unmarshaller for `A`
    */
  def unmarshallJson[A: Decoder]: FromStringUnmarshaller[A] = unmarshaller { value =>
    parse(value).left.map(err => WrongOrInvalidJson(Try(err.message).toOption))
  }

  private def unmarshaller[A](f: String => Either[Throwable, Json] )(
      implicit dec: Decoder[A]): FromStringUnmarshaller[A] =
    Unmarshaller.strict[String, A] {
      case "" => throw Unmarshaller.NoContentException
      case string =>
        f(string).flatMap(_.as[A]) match {
          case Right(value) => value
          case Left(err)    => throw new IllegalArgumentException(err)
        }
    }

}

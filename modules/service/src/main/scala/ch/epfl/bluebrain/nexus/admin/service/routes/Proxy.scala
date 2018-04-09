package ch.epfl.bluebrain.nexus.admin.service.routes

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}

import scala.concurrent.Future

/**
  * Proxy definition
  */
trait Proxy {

  /**
    * Forwards the HTTP request ''req''.
    *
    * @param req the [[HttpRequest]] to be forwarded tot he uri present within it
    * @param as  the implicitly available [[ActorSystem]]
    * @return a [[HttpResponse]] wrapped in a [[Future]]
    */
  def apply(req: HttpRequest)(implicit as: ActorSystem): Future[HttpResponse]
}

object Proxy {

  /**
    * Implementation of proxy using Akka Streams
    */
  object AkkaStream extends Proxy {
    // $COVERAGE-OFF$
    override def apply(req: HttpRequest)(implicit as: ActorSystem): Future[HttpResponse] = {
      implicit val _ = ActorMaterializer()
      Source.single(req).via(Http(as).outgoingConnection(req.uri.authority.host.address())).runWith(Sink.head)
    }
    // $COVERAGE-ON$
  }

}

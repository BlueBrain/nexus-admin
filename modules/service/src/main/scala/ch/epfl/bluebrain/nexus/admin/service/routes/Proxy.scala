package ch.epfl.bluebrain.nexus.admin.service.routes

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}

import scala.concurrent.Future

/**
  * Proxy definition
  */
trait Proxy {

  /**
    * Forwards the HTTP request ''req''.
    *
    * @param req the [[HttpRequest]] to be forwarded tot he uri present within it
    * @return a [[HttpResponse]] wrapped in a [[Future]]
    */
  def apply(req: HttpRequest): Future[HttpResponse]
}

object Proxy {

  /**
    * Implementation of proxy using Akka Streams
    */
  object AkkaStream {

    def apply()(implicit as: ActorSystem): Proxy = (req: HttpRequest) => Http().singleRequest(req)
  }

}

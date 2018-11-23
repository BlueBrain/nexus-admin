package ch.epfl.bluebrain.nexus.admin
import cats.effect.IO
import org.scalactic.source
import org.scalatest.concurrent.ScalaFutures

trait IOValues extends ScalaFutures {

  implicit class IOFutureSyntax[A](io: IO[A]) {

    def ioValue(implicit config: PatienceConfig, pos: source.Position): A = io.unsafeToFuture().futureValue(config, pos)
  }

}

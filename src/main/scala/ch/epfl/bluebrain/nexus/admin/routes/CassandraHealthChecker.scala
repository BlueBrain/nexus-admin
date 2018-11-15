package ch.epfl.bluebrain.nexus.admin.routes

import akka.Done
import akka.actor.ActorSystem
import akka.event.Logging
import akka.persistence.cassandra.CassandraPluginConfig
import akka.persistence.cassandra.session.scaladsl.CassandraSession
import ch.epfl.bluebrain.nexus.admin.config.AppConfig.PersistenceConfig
import ch.epfl.bluebrain.nexus.admin.routes.HealthChecker._

import scala.concurrent.{ExecutionContext, Future}

/**
  *  The Cassandra health checker
  */
class CassandraHealthChecker(persistence: PersistenceConfig)(implicit as: ActorSystem) extends HealthChecker {
  private implicit val ec: ExecutionContext = as.dispatcher

  private val log     = Logging(as, "CassandraHeathCheck")
  private val config  = new CassandraPluginConfig(as, as.settings.config.getConfig(persistence.journalPlugin))
  private val (p, s)  = (config.sessionProvider, config.sessionSettings)
  private val session = new CassandraSession(as, p, s, ec, log, "health", _ => Future.successful(Done.done()))
  private val query   = s"SELECT now() FROM ${config.keyspace}.messages;"

  override def check: Future[Status] =
    session
      .selectOne(query)
      .map(_ => Up: Status)
      .recover {
        case err =>
          log.error("Error while attempting to query for health check", err)
          Inaccessible
      }
}

object CassandraHealthChecker {
  def apply(persistence: PersistenceConfig)(implicit as: ActorSystem): CassandraHealthChecker =
    new CassandraHealthChecker(persistence)(as)
}

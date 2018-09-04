package ch.epfl.bluebrain.nexus.admin.core.config

import java.time.Clock

import akka.http.scaladsl.model.Uri
import ch.epfl.bluebrain.nexus.admin.core.config.AppConfig._
import ch.epfl.bluebrain.nexus.admin.refined.ld.Namespace
import ch.epfl.bluebrain.nexus.commons.http.ContextUri
import ch.epfl.bluebrain.nexus.commons.http.JsonLdCirceSupport.OrderedKeys
import ch.epfl.bluebrain.nexus.service.http.Path
import ch.epfl.bluebrain.nexus.commons.types.search.Pagination
import eu.timepit.refined.api.Refined
import eu.timepit.refined.numeric.{NonNegative, Positive}

import scala.concurrent.duration.{Duration, FiniteDuration}

/**
  * Case class which aggregates the configuration parameters
  *
  * @param description the service description namespace
  * @param instance    the service instance specific settings
  * @param http        the HTTP binding settings
  * @param runtime     the service runtime settings
  * @param cluster     the cluster specific settings
  * @param persistence the persistence settings
  * @param projects    the project specific settings
  * @param prefixes    the collection of prefixes used throughout the service
  * @param iam         the IAM connection settings
  * @param pagination  the routes pagination settings
  * @param order       the ordering of the JSON keys on the response payload
  */
final case class AppConfig(description: DescriptionConfig,
                           instance: InstanceConfig,
                           sparql: SparqlConfig,
                           http: HttpConfig,
                           runtime: RuntimeConfig,
                           cluster: ClusterConfig,
                           persistence: PersistenceConfig,
                           projects: ProjectsConfig,
                           organizations: OrganizationsConfig,
                           prefixes: PrefixesConfig,
                           iam: IamConfig,
                           pagination: PaginationConfig,
                           order: OrderKeysConfig,
                           kafka: KafkaConfig)

object AppConfig {

  implicit val clock: Clock = Clock.systemUTC

  final case class DescriptionConfig(name: String) {
    val version: String = BuildInfo.version
    val actorSystemName = s"$name-${version.replaceAll("\\W", "-")}"
  }

  final case class InstanceConfig(interface: String)

  final case class HttpConfig(interface: String, port: Int, prefix: String, publicUri: Uri) {
    val apiUri = publicUri.copy(path = (publicUri.path: Path) ++ Path(prefix))
  }

  final case class RuntimeConfig(defaultTimeout: FiniteDuration)

  final case class ClusterConfig(passivationTimeout: Duration, shards: Int, seeds: Option[String]) {
    lazy val seedAddresses: Set[String] =
      seeds.map(_.split(",").toSet).getOrElse(Set.empty[String])
  }

  final case class SparqlConfig(base: Uri, namespace: String, username: Option[String], password: Option[String]) {
    def credentials: Option[SparqlCredentials] = {
      for {
        user <- username
        pass <- password
      } yield SparqlCredentials(user, pass)
    }
  }
  final case class SparqlCredentials(username: String, password: String)

  final case class PersistenceConfig(journalPlugin: String,
                                     snapshotStorePlugin: String,
                                     queryJournalPlugin: String,
                                     defaultTag: String)

  final case class OrgConfig(name: String)

  final case class ProjectsConfig(passivationTimeout: FiniteDuration, namespace: Namespace, attachmentSize: Long)

  final case class OrganizationsConfig(passivationTimeout: FiniteDuration, namespace: Namespace)

  final case class PaginationConfig(from: Long Refined NonNegative,
                                    size: Int Refined Positive,
                                    maxSize: Int Refined Positive) {
    val pagination: Pagination = Pagination(from.value, size.value)
  }

  final case class PrefixesConfig(coreContext: ContextUri,
                                  standardsContext: ContextUri,
                                  linksContext: ContextUri,
                                  searchContext: ContextUri,
                                  distributionContext: ContextUri,
                                  errorContext: ContextUri)

  final case class IamConfig(baseUri: Uri)

  final case class OrderKeysConfig(responseKeys: List[String]) {
    val keys: OrderedKeys = OrderedKeys(responseKeys)
  }

  final case class KafkaConfig(topic: String)

  implicit def prefixesFromImplicit(implicit appConfig: AppConfig): PrefixesConfig = appConfig.prefixes

  implicit def httpFromImplicit(implicit appConfig: AppConfig): HttpConfig = appConfig.http

  implicit def descriptionFromImplicit(implicit appConfig: AppConfig): DescriptionConfig = appConfig.description

  implicit def projectsConfigFromImplicit(implicit appConfig: AppConfig): ProjectsConfig = appConfig.projects
  implicit def organizationsConfigFromImplicit(implicit appConfig: AppConfig): OrganizationsConfig =
    appConfig.organizations

  implicit def coreContextUri(implicit appConfig: AppConfig): ContextUri = appConfig.prefixes.coreContext

  implicit def orderedKeysFromImplicit(implicit appConfig: AppConfig): OrderedKeys = appConfig.order.keys

}

package ch.epfl.bluebrain.nexus.admin.core.config

import java.time.Clock

import akka.http.scaladsl.model.Uri
import ch.epfl.bluebrain.nexus.admin.core.config.AppConfig._
import ch.epfl.bluebrain.nexus.admin.refined.ld.PrefixValue
import scala.concurrent.duration.{Duration, FiniteDuration}

/**
  * Case class which aggregates the configuration parameters
  *
  * @param description  the service description namespace
  * @param instance     the service instance specific settings
  * @param http         the HTTP binding settings
  * @param runtime      the service runtime settings
  * @param cluster      the cluster specific settings
  * @param persistence  the persistence settings
  * @param projects     the project specific settings
  * @param prefixes     the collection of prefixes used throughout the service
  * @param iam          IAM connection settings
  */
final case class AppConfig(description: DescriptionConfig,
                           instance: InstanceConfig,
                           http: HttpConfig,
                           runtime: RuntimeConfig,
                           cluster: ClusterConfig,
                           persistence: PersistenceConfig,
                           projects: ProjectsConfig,
                           prefixes: PrefixesConfig,
                           iam: IamConfig)

object AppConfig {

  implicit val clock: Clock = Clock.systemUTC

  final case class DescriptionConfig(name: String, environment: String) {
    val version: String = BuildInfo.version
    val ActorSystemName = s"$name-${version.replaceAll("\\.", "-")}-$environment"
  }

  final case class InstanceConfig(interface: String)

  final case class HttpConfig(interface: String, port: Int, prefix: String, publicUri: Uri)

  final case class RuntimeConfig(defaultTimeout: FiniteDuration)

  final case class ClusterConfig(passivationTimeout: Duration, shards: Int, seeds: Option[String]) {
    lazy val seedAddresses: Set[String] =
      seeds.map(_.split(",").toSet).getOrElse(Set.empty[String])
  }

  final case class PersistenceConfig(journalPlugin: String, snapshotStorePlugin: String, queryJournalPlugin: String)

  final case class OrgConfig(name: String)

  final case class ProjectsConfig(passivationTimeout: Duration, prefixValue: PrefixValue)

  final case class PrefixesConfig(coreContext: Uri,
                                  standardsContext: Uri,
                                  linksContext: Uri,
                                  searchContext: Uri,
                                  distributionContext: Uri,
                                  errorContext: Uri)

  final case class IamConfig(baseUri: Uri)

  implicit def httpFromImplicit(implicit appConfig: AppConfig): HttpConfig = appConfig.http

  implicit def projectsConfigFromImplicit(implicit appConfig: AppConfig): ProjectsConfig = appConfig.projects

}

package ch.epfl.bluebrain.nexus.admin.core.config

import java.time.Clock

import akka.http.scaladsl.model.Uri
import ch.epfl.bluebrain.nexus.admin.core.config.AppConfig._
import ch.epfl.bluebrain.nexus.admin.refined.ld.PrefixValue
import ch.epfl.bluebrain.nexus.commons.http.ContextUri
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
  * @param iam         IAM connection settings
  * @param pagination  Routes pagination settings
  */
final case class AppConfig(description: DescriptionConfig,
                           instance: InstanceConfig,
                           http: HttpConfig,
                           runtime: RuntimeConfig,
                           cluster: ClusterConfig,
                           persistence: PersistenceConfig,
                           projects: ProjectsConfig,
                           prefixes: PrefixesConfig,
                           iam: IamConfig,
                           pagination: PaginationConfig)

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

  implicit def httpFromImplicit(implicit appConfig: AppConfig): HttpConfig = appConfig.http

  implicit def projectsConfigFromImplicit(implicit appConfig: AppConfig): ProjectsConfig = appConfig.projects

}

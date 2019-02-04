package ch.epfl.bluebrain.nexus.admin.config

import akka.http.scaladsl.model.Uri
import ch.epfl.bluebrain.nexus.admin.config.AppConfig._
import ch.epfl.bluebrain.nexus.admin.config.Vocabulary._
import ch.epfl.bluebrain.nexus.commons.http.JsonLdCirceSupport.OrderedKeys
import ch.epfl.bluebrain.nexus.commons.types.search.Pagination
import ch.epfl.bluebrain.nexus.iam.client.config.IamClientConfig
import ch.epfl.bluebrain.nexus.iam.client.types.{AuthToken, Permission}
import ch.epfl.bluebrain.nexus.rdf.Iri.AbsoluteIri
import ch.epfl.bluebrain.nexus.rdf.syntax.node.unsafe._
import ch.epfl.bluebrain.nexus.service.indexer.cache.KeyValueStoreConfig
import ch.epfl.bluebrain.nexus.service.kamon.directives.TracingDirectives
import ch.epfl.bluebrain.nexus.sourcing.akka.SourcingConfig
import ch.epfl.bluebrain.nexus.sourcing.akka.SourcingConfig.RetryStrategyConfig

import scala.concurrent.duration.FiniteDuration

/**
  * Application configuration
  *
  * @param description    service description
  * @param http           http interface configuration
  * @param cluster        akka cluster configuration
  * @param persistence    persistence configuration
  * @param indexing       Indexing configuration
  * @param keyValueStore  Distributed data configuration
  * @param sourcing       Sourcing configuration
  * @param iam            IAM configuration
  * @param pagination     pagination configuration
  * @param serviceAccount service account configuration
  * @param permissions    permissions configuration
  */
final case class AppConfig(description: Description,
                           http: HttpConfig,
                           cluster: ClusterConfig,
                           persistence: PersistenceConfig,
                           indexing: IndexingConfig,
                           keyValueStore: KeyValueStoreConfig,
                           sourcing: SourcingConfig,
                           iam: IamClientConfig,
                           pagination: PaginationConfig,
                           serviceAccount: ServiceAccountConfig,
                           permissions: PermissionsConfig)

object AppConfig {

  /**
    * Service description
    *
    * @param name service name
    */
  final case class Description(name: String) {

    /**
      * The service version
      */
    val version: String = BuildInfo.version

    /**
      * The full name of the service (name + version)
      */
    val fullName: String = s"$name-${version.replaceAll("\\W", "-")}"

  }

  /**
    * HTTP configuration
    *
    * @param interface  interface to bind to
    * @param port       port to bind to
    * @param prefix     prefix to add to HTTP routes
    * @param publicUri  public URI of the service
    */
  final case class HttpConfig(interface: String, port: Int, prefix: String, publicUri: Uri) {

    /**
      * The public IRI of the service.
      */
    val publicIri: AbsoluteIri = url"$publicUri".value

    /**
      * The public IRI of the service including the http prefix.
      */
    val prefixIri: AbsoluteIri = url"$publicUri/$prefix".value

    /**
      * The root IRI for projects
      */
    val projectsIri: AbsoluteIri = prefixIri + "projects"

    /**
      * The base IRI for organizations
      */
    val orgsBaseIri: AbsoluteIri = prefixIri + "orgs"
  }

  /**
    * Cluster configuration
    *
    * @param passivationTimeout actor passivation timeout
    * @param replicationTimeout replication / distributed data timeout
    * @param shards             number of shards in the cluster
    * @param seeds              seed nodes in the cluster
    */
  final case class ClusterConfig(passivationTimeout: FiniteDuration,
                                 replicationTimeout: FiniteDuration,
                                 shards: Int,
                                 seeds: Option[String])

  /**
    * Persistence configuration
    *
    * @param journalPlugin        plugin for storing events
    * @param snapshotStorePlugin  plugin for storing snapshots
    * @param queryJournalPlugin   plugin for querying journal events
    */
  final case class PersistenceConfig(journalPlugin: String, snapshotStorePlugin: String, queryJournalPlugin: String)

  /**
    * Indexing configuration
    *
    * @param batch        the maximum number of events taken on each batch
    * @param batchTimeout the maximum amount of time to wait for the number of events to be taken on each batch
    * @param retry        the retry configuration when indexing failures
    */
  final case class IndexingConfig(batch: Int, batchTimeout: FiniteDuration, retry: RetryStrategyConfig)

  /**
    * Kafka configuration.
    *
    * @param topic  topic to publish events.
    */
  final case class KafkaConfig(topic: String)

  /**
    * Service account configuration.
    *
    * @param token token to be used for communication with IAM, if not present anonymous account will be used.
    */
  final case class ServiceAccountConfig(token: Option[String]) {
    def credentials: Option[AuthToken] = token.map(AuthToken)
  }

  /**
    * Permissions configuration.
    *
    * @param owner  permissions applied to the creator of the project.
    */
  final case class PermissionsConfig(owner: Set[String], retry: RetryStrategyConfig) {

    def ownerPermissions: Set[Permission] = owner.map(Permission.unsafe)
  }

  /**
    * Pagination configuration
    *
    * @param size    the default results size
    * @param maxSize the maximum results size
    */
  final case class PaginationConfig(size: Int, maxSize: Int) {
    val default: Pagination = Pagination(0, size)
  }

  val orderedKeys = OrderedKeys(
    List(
      "@context",
      "@id",
      "@type",
      "code",
      "message",
      "details",
      nxv.reason.prefix,
      nxv.total.prefix,
      nxv.maxScore.prefix,
      nxv.results.prefix,
      nxv.score.prefix,
      nxv.description.name,
      nxv.`@base`.name,
      nxv.`@vocab`.name,
      nxv.apiMappings.name,
      nxv.prefix.name,
      nxv.namespace.name,
      "",
      nxv.uuid.prefix,
      nxv.label.prefix,
      nxv.organizationUuid.prefix,
      nxv.organizationLabel.prefix,
      nxv.self.prefix,
      nxv.constrainedBy.prefix,
      nxv.project.prefix,
      nxv.rev.prefix,
      nxv.deprecated.prefix,
      nxv.createdAt.prefix,
      nxv.createdBy.prefix,
      nxv.updatedAt.prefix,
      nxv.updatedBy.prefix,
      nxv.instant.prefix,
      nxv.subject.prefix,
    ))

  val tracing = new TracingDirectives()

}

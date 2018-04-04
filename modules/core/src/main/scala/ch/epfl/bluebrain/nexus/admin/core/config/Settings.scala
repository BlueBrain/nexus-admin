package ch.epfl.bluebrain.nexus.admin.core.config

import akka.actor.{ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider}
import akka.http.scaladsl.model.Uri
import ch.epfl.bluebrain.nexus.admin.core.config.AppConfig._
import ch.epfl.bluebrain.nexus.admin.refined.ld.Id
import ch.epfl.bluebrain.nexus.commons.http.ContextUri
import com.typesafe.config.Config
import eu.timepit.refined.pureconfig._
import pureconfig.ConvertHelpers._
import pureconfig.{ConfigConvert, loadConfigOrThrow}
import eu.timepit.refined.api.RefType.applyRef
import pureconfig.error.{CannotConvert, FailureReason}

/**
  * Akka settings extension to expose application configuration.  It typically uses the configuration instance of the
  * actor system as the configuration root.
  *
  * @param config the configuration instance to read
  */
@SuppressWarnings(Array("LooksLikeInterpolatedString"))
class Settings(config: Config) extends Extension {

  private implicit val uriConverter: ConfigConvert[Uri] =
    ConfigConvert.viaString[Uri](catchReadError(s => Uri(s)), _.toString)

  private implicit val contextUriConverter: ConfigConvert[ContextUri] =
    ConfigConvert.viaString[ContextUri](s =>
                                          applyRef[Id](s)
                                            .map(id => ContextUri(id.value))
                                            .left
                                            .map[FailureReason](err => CannotConvert(s, "ContextUri", err)),
                                        _.toString)

  val appConfig = AppConfig(
    loadConfigOrThrow[DescriptionConfig](config, "app.description"),
    loadConfigOrThrow[InstanceConfig](config, "app.instance"),
    loadConfigOrThrow[HttpConfig](config, "app.http"),
    loadConfigOrThrow[RuntimeConfig](config, "app.runtime"),
    loadConfigOrThrow[ClusterConfig](config, "app.cluster"),
    loadConfigOrThrow[PersistenceConfig](config, "app.persistence"),
    loadConfigOrThrow[ProjectsConfig](config, "app.projects"),
    loadConfigOrThrow[PrefixesConfig](config, "app.prefixes"),
    loadConfigOrThrow[IamConfig](config, "app.iam"),
    loadConfigOrThrow[PaginationConfig](config, "app.pagination"),
    loadConfigOrThrow[OrderKeysConfig](config, "app.order")
  )

}

object Settings extends ExtensionId[Settings] with ExtensionIdProvider {
  // $COVERAGE-OFF$
  override def lookup(): ExtensionId[_ <: Extension] = Settings

  override def createExtension(system: ExtendedActorSystem): Settings = new Settings(system.settings.config)

  def apply(config: Config): Settings = new Settings(config)
  // $COVERAGE-ON$
}

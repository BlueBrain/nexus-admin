/*
scalafmt: {
  style = defaultWithAlign
  maxColumn = 150
  align.tokens = [
    { code = "=>", owner = "Case" }
    { code = "?", owner = "Case" }
    { code = "extends", owner = "Defn.(Class|Trait|Object)" }
    { code = "//", owner = ".*" }
    { code = "{", owner = "Template" }
    { code = "}", owner = "Template" }
    { code = ":=", owner = "Term.ApplyInfix" }
    { code = "++=", owner = "Term.ApplyInfix" }
    { code = "+=", owner = "Term.ApplyInfix" }
    { code = "%", owner = "Term.ApplyInfix" }
    { code = "%%", owner = "Term.ApplyInfix" }
    { code = "%%%", owner = "Term.ApplyInfix" }
    { code = "->", owner = "Term.ApplyInfix" }
    { code = "?", owner = "Term.ApplyInfix" }
    { code = "<-", owner = "Enumerator.Generator" }
    { code = "?", owner = "Enumerator.Generator" }
    { code = "=", owner = "(Enumerator.Val|Defn.(Va(l|r)|Def|Type))" }
  ]
}
 */

// Dependency versions
val akkaVersion                     = "2.5.11"
val akkaHttpVersion                 = "10.0.11"
val akkaPersistenceInMemVersion     = "2.5.1.1"
val akkaPersistenceCassandraVersion = "0.83"
val catsVersion                     = "1.0.1"
val circeVersion                    = "0.9.1"
val jenaVersion                     = "3.6.0"
val mockitoVersion                  = "2.15.0"
val pureconfigVersion               = "0.9.0"
val refinedVersion                  = "0.8.7"
val scalaTestVersion                = "3.0.5"
val shapelessVersion                = "2.3.3"
val sourcingVersion                 = "0.10.3"

// Nexus dependency versions
val serviceVersion = "0.10.4"
val commonsVersion = "0.10.4"

// Dependency modules
lazy val akkaDistributed      = "com.typesafe.akka"     %% "akka-distributed-data"     % akkaVersion
lazy val akkaHttpCore         = "com.typesafe.akka"     %% "akka-http-core"            % akkaHttpVersion
lazy val akkaPersistenceInMem = "com.github.dnvriend"   %% "akka-persistence-inmemory" % akkaPersistenceInMemVersion
lazy val akkaHttpTestKit      = "com.typesafe.akka"     %% "akka-http-testkit"         % akkaHttpVersion
lazy val akkaTestKit          = "com.typesafe.akka"     %% "akka-testkit"              % akkaVersion
lazy val catsCore             = "org.typelevel"         %% "cats-core"                 % catsVersion
lazy val circeCore            = "io.circe"              %% "circe-core"                % circeVersion
lazy val circeJava8           = "io.circe"              %% "circe-java8"               % circeVersion
lazy val jenaArq              = "org.apache.jena"       % "jena-arq"                   % jenaVersion
lazy val mockitoCore          = "org.mockito"           % "mockito-core"               % mockitoVersion
lazy val pureconfig           = "com.github.pureconfig" %% "pureconfig"                % pureconfigVersion
lazy val shapeless            = "com.chuusai"           %% "shapeless"                 % shapelessVersion
lazy val scalaTest            = "org.scalatest"         %% "scalatest"                 % scalaTestVersion
lazy val slf4j                = "com.typesafe.akka"     %% "akka-slf4j"                % akkaVersion

lazy val refined           = "eu.timepit" %% "refined"            % refinedVersion
lazy val refinedPureConfig = "eu.timepit" %% "refined-pureconfig" % refinedVersion

// Nexus dependency modules
lazy val commonsIam        = "ch.epfl.bluebrain.nexus" %% "iam"                   % commonsVersion
lazy val commonsQueryTypes = "ch.epfl.bluebrain.nexus" %% "commons-query-types"   % commonsVersion
lazy val commonsTest       = "ch.epfl.bluebrain.nexus" %% "commons-test"          % commonsVersion
lazy val serialization     = "ch.epfl.bluebrain.nexus" %% "service-serialization" % serviceVersion
lazy val serviceHttp       = "ch.epfl.bluebrain.nexus" %% "service-http"          % serviceVersion
lazy val sourcingCore      = "ch.epfl.bluebrain.nexus" %% "sourcing-core"         % sourcingVersion
lazy val sourcingCache     = "ch.epfl.bluebrain.nexus" %% "sourcing-akka-cache"   % sourcingVersion
lazy val sourcingMem       = "ch.epfl.bluebrain.nexus" %% "sourcing-mem"          % sourcingVersion

// Projects
lazy val refinements = project
  .in(file("modules/refined"))
  .settings(
    name                := "admin-refined",
    moduleName          := "admin-refined",
    coverageEnabled     := false,
    libraryDependencies ++= Seq(akkaHttpCore, commonsIam, refined, scalaTest % Test)
  )
lazy val ld = project
  .dependsOn(refinements)
  .in(file("modules/ld"))
  .settings(
    name       := "admin-ld",
    moduleName := "admin-ld",
    libraryDependencies ++= Seq(
      catsCore,
      circeCore,
      commonsTest,
      jenaArq,
      shapeless,
      scalaTest % Test
    )
  )

lazy val query = project
  .in(file("modules/query"))
  .enablePlugins(BuildInfoPlugin)
  .dependsOn(ld)
  .settings(
    buildInfoSettings,
    name       := "admin-query",
    moduleName := "admin-query",
    libraryDependencies ++= Seq(
      commonsIam,
      commonsQueryTypes,
      scalaTest % Test,
    )
  )

lazy val core = project
  .in(file("modules/core"))
  .enablePlugins(BuildInfoPlugin)
  .dependsOn(query)
  .settings(
    buildInfoSettings,
    name       := "admin-core",
    moduleName := "admin-core",
    libraryDependencies ++= Seq(
      circeJava8,
      commonsQueryTypes,
      pureconfig,
      refinedPureConfig,
      sourcingCore,
      sourcingCache,
      serialization,
      serviceHttp,
      akkaDistributed      % Test,
      akkaPersistenceInMem % Test,
      akkaHttpTestKit      % Test,
      akkaTestKit          % Test,
      mockitoCore          % Test,
      scalaTest            % Test,
      sourcingMem          % Test,
      slf4j                % Test
    )
  )

lazy val service = project
  .in(file("modules/service"))
  .enablePlugins(BuildInfoPlugin)
  .dependsOn(core)
  .settings(
    buildInfoSettings,
    name                  := "admin-service",
    moduleName            := "admin-service",
    coverageFailOnMinimum := false,
    libraryDependencies ++= Seq(
      akkaDistributed      % Test,
      akkaPersistenceInMem % Test,
      akkaHttpTestKit      % Test,
      akkaTestKit          % Test,
      mockitoCore          % Test,
      scalaTest            % Test,
      sourcingMem          % Test,
      slf4j                % Test
    )
  )

lazy val root = project
  .in(file("."))
  .settings(noPublish)
  .settings(
    name                  := "admin",
    moduleName            := "admin",
    coverageFailOnMinimum := false
  )
  .aggregate(refinements, ld, query, core, service)

/* ********************************************************
 ******************** Grouped Settings ********************
 **********************************************************/

lazy val noPublish = Seq(
  publishLocal    := {},
  publish         := {},
  publishArtifact := false,
)

lazy val buildInfoSettings =
  Seq(buildInfoKeys := Seq[BuildInfoKey](version), buildInfoPackage := "ch.epfl.bluebrain.nexus.admin.core.config")

inThisBuild(
  List(
    homepage := Some(url("https://github.com/BlueBrain/nexus-admin")),
    licenses := Seq("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0.txt")),
    scmInfo  := Some(ScmInfo(url("https://github.com/BlueBrain/nexus-admin"), "scm:git:git@github.com:BlueBrain/nexus-admin.git")),
    developers := List(
      Developer("bogdanromanx", "Bogdan Roman", "noreply@epfl.ch", url("https://bluebrain.epfl.ch/")),
      Developer("hygt", "Henry Genet", "noreply@epfl.ch", url("https://bluebrain.epfl.ch/")),
      Developer("umbreak", "Didac Montero Mendez", "noreply@epfl.ch", url("https://bluebrain.epfl.ch/")),
      Developer("wwajerowicz", "Wojtek Wajerowicz", "noreply@epfl.ch", url("https://bluebrain.epfl.ch/")),
    ),
    // These are the sbt-release-early settings to configure
    releaseEarlyWith              := BintrayPublisher,
    releaseEarlyNoGpg             := true,
    releaseEarlyEnableSyncToMaven := false,
  ))

addCommandAlias("review", ";clean;scalafmtSbtCheck;coverage;scapegoat;test;coverageReport;coverageAggregate")

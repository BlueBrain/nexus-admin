
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
val akkaHttpCorsVersion             = "0.2.2"
val akkaPersistenceInMemVersion     = "2.5.1.1"
val akkaPersistenceCassandraVersion = "0.83"
val catsVersion                     = "1.1.0"
val circeVersion                    = "0.9.2"
val jenaVersion                     = "3.6.0"
val mockitoVersion                  = "2.16.0"
val pureconfigVersion               = "0.9.0"
val refinedVersion                  = "0.8.7"
val scalaTestVersion                = "3.0.5"
val shapelessVersion                = "2.3.3"
val sourcingVersion                 = "0.10.3"

// Nexus dependency versions
val serviceVersion = "0.10.6"
val commonsVersion = "0.10.8"

// Dependency modules
lazy val akkaDistributed          = "com.typesafe.akka"     %% "akka-distributed-data"      % akkaVersion
lazy val akkaHttpCore             = "com.typesafe.akka"     %% "akka-http-core"             % akkaHttpVersion
lazy val akkaPersistence          = "com.typesafe.akka"     %% "akka-persistence"           % akkaVersion
lazy val akkaPersistenceCassandra = "com.typesafe.akka"     %% "akka-persistence-cassandra" % akkaPersistenceCassandraVersion
lazy val akkaPersistenceInMem     = "com.github.dnvriend"   %% "akka-persistence-inmemory"  % akkaPersistenceInMemVersion
lazy val akkaHttpTestKit          = "com.typesafe.akka"     %% "akka-http-testkit"          % akkaHttpVersion
lazy val akkaTestKit              = "com.typesafe.akka"     %% "akka-testkit"               % akkaVersion
lazy val catsCore                 = "org.typelevel"         %% "cats-core"                  % catsVersion
lazy val circeCore                = "io.circe"              %% "circe-core"                 % circeVersion
lazy val circeJava8               = "io.circe"              %% "circe-java8"                % circeVersion
lazy val circeRefined             = "io.circe"              %% "circe-refined"              % circeVersion
lazy val jenaArq                  = "org.apache.jena"       % "jena-arq"                    % jenaVersion
lazy val mockitoCore              = "org.mockito"           % "mockito-core"                % mockitoVersion
lazy val pureconfig               = "com.github.pureconfig" %% "pureconfig"                 % pureconfigVersion
lazy val shapeless                = "com.chuusai"           %% "shapeless"                  % shapelessVersion
lazy val scalaTest                = "org.scalatest"         %% "scalatest"                  % scalaTestVersion
lazy val slf4j                    = "com.typesafe.akka"     %% "akka-slf4j"                 % akkaVersion

lazy val refined           = "eu.timepit" %% "refined"            % refinedVersion
lazy val refinedPureConfig = "eu.timepit" %% "refined-pureconfig" % refinedVersion

// Nexus dependency modules
lazy val commonsIam        = "ch.epfl.bluebrain.nexus" %% "iam"                   % commonsVersion
lazy val commonsQueryTypes = "ch.epfl.bluebrain.nexus" %% "commons-query-types"   % commonsVersion
lazy val commonsSchemas = "ch.epfl.bluebrain.nexus" %% "commons-schemas"   % commonsVersion
lazy val commonsTest       = "ch.epfl.bluebrain.nexus" %% "commons-test"          % commonsVersion
lazy val serialization     = "ch.epfl.bluebrain.nexus" %% "service-serialization" % serviceVersion
lazy val serviceHttp       = "ch.epfl.bluebrain.nexus" %% "service-http"          % serviceVersion
lazy val serviceKamon      = "ch.epfl.bluebrain.nexus" %% "service-kamon"         % serviceVersion
lazy val sourcingAkka      = "ch.epfl.bluebrain.nexus" %% "sourcing-akka"         % sourcingVersion
lazy val sourcingCore      = "ch.epfl.bluebrain.nexus" %% "sourcing-core"         % sourcingVersion
lazy val sourcingMem       = "ch.epfl.bluebrain.nexus" %% "sourcing-mem"          % sourcingVersion
lazy val akkaHttpCors      = "ch.megard"               %% "akka-http-cors"        % akkaHttpCorsVersion

// Projects
lazy val refinements = project
  .in(file("modules/refined"))
  .settings(
    name                     := "admin-refined",
    moduleName               := "admin-refined",
    coverageEnabled          := false,
    libraryDependencies      ++= Seq(akkaHttpCore, commonsIam, refined, scalaTest % Test),
    Test / fork              := true,
    Test / parallelExecution := false // workaround for jena initialization
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
    ),
    Test / fork              := true,
    Test / parallelExecution := false // workaround for jena initialization
  )

lazy val schemas = project
  .in(file("modules/schemas"))
  .enablePlugins(WorkbenchPlugin,BuildInfoPlugin)
  .disablePlugins(ScapegoatSbtPlugin, DocumentationPlugin)
  .settings(
    name := "admin-schemas",
    moduleName := "admin-schemas",
    libraryDependencies ++= Seq(
      commonsSchemas
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
    ),
    Test / fork              := true,
    Test / parallelExecution := false // workaround for jena initialization
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
      akkaPersistence,
      circeJava8,
      circeRefined,
      commonsQueryTypes,
      pureconfig,
      refinedPureConfig,
      serialization,
      sourcingCore,
      akkaDistributed      % Test,
      akkaHttpTestKit      % Test,
      akkaPersistenceInMem % Test,
      scalaTest            % Test,
      sourcingMem          % Test,
      slf4j                % Test
    ),
    Test / fork              := true,
    Test / parallelExecution := false // workaround for jena initialization
  )

lazy val service = project
  .in(file("modules/service"))
  .enablePlugins(BuildInfoPlugin, ServicePackagingPlugin)
  .dependsOn(core % testAndCompile)
  .settings(
    buildInfoSettings,
    name       := "admin-service",
    moduleName := "admin-service",
    libraryDependencies ++= Seq(
      akkaDistributed,
      akkaHttpCors,
      akkaPersistenceCassandra,
      serviceHttp,
      serviceKamon,
      sourcingAkka,
      slf4j,
      akkaTestKit % Test,
      mockitoCore % Test
    ),
    Test / fork              := true,
    Test / parallelExecution := false // workaround for jena initialization
  )

lazy val root = project
  .in(file("."))
  .settings(noPublish)
  .settings(
    name                  := "admin",
    moduleName            := "admin",
    coverageFailOnMinimum := false
  )
  .aggregate(refinements, ld, query, core, service,schemas)

/* ********************************************************
 ******************** Grouped Settings ********************
 **********************************************************/

lazy val noPublish = Seq(
  publishLocal    := {},
  publish         := {},
  publishArtifact := false,
)

lazy val testAndCompile = "test->test;compile->compile"

lazy val buildInfoSettings =
  Seq(buildInfoKeys := Seq[BuildInfoKey](version), buildInfoPackage := "ch.epfl.bluebrain.nexus.admin.core.config")

inThisBuild(
  List(
    workbenchVersion := "0.3.0",
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

addCommandAlias("review", ";clean;scalafmtCheck;scalafmtSbtCheck;test:scalafmtCheck;coverage;scapegoat;test;coverageReport;coverageAggregate")

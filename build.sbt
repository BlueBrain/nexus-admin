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
val alpakkaVersion             = "1.0-M3"
val rdfVersion                 = "0.3.1"
val commonsVersion             = "0.11.1"
val iamVersion                 = "76462380"
val sourcingVersion            = "0.13.0"
val akkaVersion                = "2.5.21"
val akkaCorsVersion            = "0.3.4"
val akkaHttpVersion            = "10.1.7"
val akkaPersistenceCassVersion = "0.92"
val akkaPersistenceMemVersion  = "2.5.15.1"
val catsVersion                = "1.6.0"
val circeVersion               = "0.11.1"
val journalVersion             = "3.0.19"
val logbackVersion             = "1.2.3"
val mockitoVersion             = "1.2.0"
val pureconfigVersion          = "0.10.2"
val scalaTestVersion           = "3.0.6"
val kryoVersion                = "0.5.2"

// Dependencies modules
lazy val rdf                 = "ch.epfl.bluebrain.nexus" %% "rdf"                        % rdfVersion
lazy val iamClient           = "ch.epfl.bluebrain.nexus" %% "iam-client"                 % iamVersion
lazy val sourcingCore        = "ch.epfl.bluebrain.nexus" %% "sourcing-core"              % sourcingVersion
lazy val sourcingStream      = "ch.epfl.bluebrain.nexus" %% "sourcing-stream"            % sourcingVersion
lazy val commonsCore         = "ch.epfl.bluebrain.nexus" %% "commons-core"               % commonsVersion
lazy val commonsTest         = "ch.epfl.bluebrain.nexus" %% "commons-test"               % commonsVersion
lazy val akkaCluster         = "com.typesafe.akka"       %% "akka-cluster"               % akkaVersion
lazy val akkaHttp            = "com.typesafe.akka"       %% "akka-http"                  % akkaHttpVersion
lazy val akkaHttpCors        = "ch.megard"               %% "akka-http-cors"             % akkaCorsVersion
lazy val akkaHttpTestKit     = "com.typesafe.akka"       %% "akka-http-testkit"          % akkaHttpVersion
lazy val akkaPersistenceCass = "com.typesafe.akka"       %% "akka-persistence-cassandra" % akkaPersistenceCassVersion
lazy val akkaPersistenceMem  = "com.github.dnvriend"     %% "akka-persistence-inmemory"  % akkaPersistenceMemVersion
lazy val akkaSlf4j           = "com.typesafe.akka"       %% "akka-slf4j"                 % akkaVersion
lazy val akkaStream          = "com.typesafe.akka"       %% "akka-stream"                % akkaVersion
lazy val akkaTestkit         = "com.typesafe.akka"       %% "akka-testkit"               % akkaVersion
lazy val alpakkaSSE          = "com.lightbend.akka"      %% "akka-stream-alpakka-sse"    % alpakkaVersion
lazy val catsCore            = "org.typelevel"           %% "cats-core"                  % catsVersion
lazy val circeCore           = "io.circe"                %% "circe-core"                 % circeVersion
lazy val journalCore         = "io.verizon.journal"      %% "core"                       % journalVersion
lazy val mockito             = "org.mockito"             %% "mockito-scala"              % mockitoVersion
lazy val logbackClassic      = "ch.qos.logback"          % "logback-classic"             % logbackVersion
lazy val pureconfig          = "com.github.pureconfig"   %% "pureconfig"                 % pureconfigVersion
lazy val kryo                = "com.github.romix.akka"   %% "akka-kryo-serialization"    % kryoVersion

lazy val admin = project
  .in(file("."))
  .settings(testSettings, buildInfoSettings)
  .enablePlugins(BuildInfoPlugin, ServicePackagingPlugin)
  .aggregate(client)
  .settings(
    name       := "admin",
    moduleName := "admin",
    libraryDependencies ++= Seq(
      akkaCluster,
      akkaHttp,
      akkaHttpCors,
      akkaPersistenceCass,
      akkaSlf4j,
      akkaStream,
      catsCore,
      circeCore,
      iamClient,
      journalCore,
      logbackClassic,
      kryo,
      pureconfig,
      rdf,
      sourcingCore,
      sourcingStream,
      akkaTestkit        % Test,
      akkaHttpTestKit    % Test,
      akkaPersistenceMem % Test,
      commonsTest        % Test,
      mockito            % Test
    ),
    resolvers += "dnvriend" at "http://dl.bintray.com/dnvriend/maven"
  )

lazy val client = project
  .in(file("client"))
  .settings(
    name                  := "admin-client",
    moduleName            := "admin-client",
    coverageFailOnMinimum := false,
    Test / testOptions    += Tests.Argument(TestFrameworks.ScalaTest, "-o", "-u", "target/test-reports"),
    libraryDependencies ++= Seq(
      akkaHttp,
      alpakkaSSE,
      catsCore,
      circeCore,
      commonsCore,
      iamClient,
      logbackClassic,
      rdf,
      akkaTestkit     % Test,
      akkaHttpTestKit % Test,
      commonsTest     % Test,
      mockito         % Test
    )
  )

lazy val testSettings = Seq(
  Test / testOptions       += Tests.Argument(TestFrameworks.ScalaTest, "-o", "-u", "target/test-reports"),
  Test / fork              := true,
  Test / parallelExecution := false, // workaround for jena initialization
  coverageFailOnMinimum    := false,
  Test / scalacOptions --= Seq("-Ywarn-dead-code", "-Ywarn-value-discard") // for mockito-scala
)

lazy val buildInfoSettings = Seq(
  buildInfoKeys    := Seq[BuildInfoKey](version),
  buildInfoPackage := "ch.epfl.bluebrain.nexus.admin.config"
)

inThisBuild(
  List(
    workbenchVersion := "0.3.2",
    homepage         := Some(url("https://github.com/BlueBrain/nexus-admin")),
    licenses         := Seq("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0.txt")),
    scmInfo          := Some(ScmInfo(url("https://github.com/BlueBrain/nexus-admin"), "scm:git:git@github.com:BlueBrain/nexus-admin.git")),
    developers := List(
      Developer("bogdanromanx", "Bogdan Roman", "noreply@epfl.ch", url("https://bluebrain.epfl.ch/")),
      Developer("hygt", "Henry Genet", "noreply@epfl.ch", url("https://bluebrain.epfl.ch/")),
      Developer("umbreak", "Didac Montero Mendez", "noreply@epfl.ch", url("https://bluebrain.epfl.ch/")),
      Developer("wwajerowicz", "Wojtek Wajerowicz", "noreply@epfl.ch", url("https://bluebrain.epfl.ch/"))
    ),
    // These are the sbt-release-early settings to configure
    releaseEarlyWith              := BintrayPublisher,
    releaseEarlyNoGpg             := true,
    releaseEarlyEnableSyncToMaven := false
  ))

addCommandAlias("review", ";clean;scalafmtCheck;scalafmtSbtCheck;test:scalafmtCheck;coverage;scapegoat;test;coverageReport;coverageAggregate")

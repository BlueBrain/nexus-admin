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
val alpakkaVersion             = "1.1.2"
val commonsVersion             = "0.22.2"
val iamVersion                 = "1.2.0+17-cb7513f6"
val sourcingVersion            = "0.20.0"
val akkaVersion                = "2.6.1"
val akkaCorsVersion            = "0.4.2"
val akkaHttpVersion            = "10.1.11"
val akkaPersistenceCassVersion = "0.100"
val akkaPersistenceMemVersion  = "2.5.15.2"
val catsVersion                = "2.1.0"
val circeVersion               = "0.12.3"
val logbackVersion             = "1.2.3"
val mockitoVersion             = "1.10.4"
val monixVersion               = "3.1.0"
val pureconfigVersion          = "0.12.2"
val scalaTestVersion           = "3.1.0"
val scalaLoggingVersion        = "3.9.2"
val splitBrainLithiumVersion   = "0.10.0"
val kryoVersion                = "1.1.0"

// Dependencies modules
lazy val iamClient           = "ch.epfl.bluebrain.nexus"    %% "iam-client"                 % iamVersion
lazy val sourcingProjections = "ch.epfl.bluebrain.nexus"    %% "sourcing-projections"       % sourcingVersion
lazy val commonsCore         = "ch.epfl.bluebrain.nexus"    %% "commons-core"               % commonsVersion
lazy val commonsKamon        = "ch.epfl.bluebrain.nexus"    %% "commons-kamon"              % commonsVersion
lazy val commonsTest         = "ch.epfl.bluebrain.nexus"    %% "commons-test"               % commonsVersion
lazy val akkaCluster         = "com.typesafe.akka"          %% "akka-cluster"               % akkaVersion
lazy val akkaHttp            = "com.typesafe.akka"          %% "akka-http"                  % akkaHttpVersion
lazy val akkaHttpCors        = "ch.megard"                  %% "akka-http-cors"             % akkaCorsVersion
lazy val akkaHttpTestKit     = "com.typesafe.akka"          %% "akka-http-testkit"          % akkaHttpVersion
lazy val akkaPersistenceCass = "com.typesafe.akka"          %% "akka-persistence-cassandra" % akkaPersistenceCassVersion
lazy val akkaPersistenceMem  = "com.github.dnvriend"        %% "akka-persistence-inmemory"  % akkaPersistenceMemVersion
lazy val akkaSlf4j           = "com.typesafe.akka"          %% "akka-slf4j"                 % akkaVersion
lazy val akkaStream          = "com.typesafe.akka"          %% "akka-stream"                % akkaVersion
lazy val akkaTestkit         = "com.typesafe.akka"          %% "akka-testkit"               % akkaVersion
lazy val alpakkaSSE          = "com.lightbend.akka"         %% "akka-stream-alpakka-sse"    % alpakkaVersion
lazy val catsCore            = "org.typelevel"              %% "cats-core"                  % catsVersion
lazy val circeCore           = "io.circe"                   %% "circe-core"                 % circeVersion
lazy val mockito             = "org.mockito"                %% "mockito-scala"              % mockitoVersion
lazy val monixEval           = "io.monix"                   %% "monix-eval"                 % monixVersion
lazy val logbackClassic      = "ch.qos.logback"             % "logback-classic"             % logbackVersion
lazy val pureconfig          = "com.github.pureconfig"      %% "pureconfig"                 % pureconfigVersion
lazy val kryo                = "io.altoo"                   %% "akka-kryo-serialization"    % kryoVersion
lazy val scalaLogging        = "com.typesafe.scala-logging" %% "scala-logging"              % scalaLoggingVersion
lazy val scalaTest           = "org.scalatest"              %% "scalatest"                  % scalaTestVersion
lazy val splitBrainLithium   = "com.swissborg"              %% "lithium"                    % splitBrainLithiumVersion

lazy val admin = project
  .in(file("."))
  .settings(testSettings, buildInfoSettings)
  .enablePlugins(BuildInfoPlugin, ServicePackagingPlugin)
  .aggregate(client)
  .settings(
    name                 := "admin",
    moduleName           := "admin",
    Docker / packageName := "nexus-admin",
    resolvers            ++= Seq("dnvriend" at "https://dl.bintray.com/dnvriend/maven", "swissborg" at "https://dl.bintray.com/swissborg/maven"),
    libraryDependencies ++= Seq(
      akkaCluster,
      akkaHttp,
      akkaHttpCors,
      akkaPersistenceCass,
      akkaSlf4j,
      akkaStream,
      catsCore,
      circeCore,
      commonsKamon,
      iamClient,
      logbackClassic,
      kryo,
      monixEval,
      pureconfig,
      scalaLogging,
      sourcingProjections,
      splitBrainLithium,
      akkaTestkit        % Test,
      akkaHttpTestKit    % Test,
      akkaPersistenceMem % Test,
      commonsTest        % Test,
      mockito            % Test,
      scalaTest          % Test
    )
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
      akkaTestkit     % Test,
      akkaHttpTestKit % Test,
      commonsTest     % Test,
      mockito         % Test,
      scalaTest       % Test
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
    homepage := Some(url("https://github.com/BlueBrain/nexus-admin")),
    licenses := Seq("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0.txt")),
    scmInfo  := Some(ScmInfo(url("https://github.com/BlueBrain/nexus-admin"), "scm:git:git@github.com:BlueBrain/nexus-admin.git")),
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
  )
)

addCommandAlias("review", ";clean;scalafmtCheck;scalafmtSbtCheck;test:scalafmtCheck;coverage;scapegoat;test;coverageReport;coverageAggregate")

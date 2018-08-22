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
val akkaVersion                 = "2.5.14"
val akkaHttpVersion             = "10.1.4"
val akkaHttpCorsVersion         = "0.3.0"
val akkaPersistenceInMemVersion = "2.5.1.1"
val catsVersion                 = "1.2.0"
val circeVersion                = "0.9.3"
val jenaVersion                 = "3.7.0"
val mockitoVersion              = "2.21.0"
val monixVersion                = "3.0.0-RC1"
val pureconfigVersion           = "0.9.1"
val refinedVersion              = "0.9.2"
val scalaTestVersion            = "3.0.5"
val shapelessVersion            = "2.3.3"
val sourcingVersion             = "0.10.7"

// Nexus dependency versions
val serviceVersion = "0.10.14"
val commonsVersion = "0.10.20"
val iamVersion     = "0.10.23"
val rdfVersion     = "0.2.18"

// Dependency modules
lazy val akkaDistributed      = "com.typesafe.akka"       %% "akka-distributed-data"       % akkaVersion
lazy val akkaHttpCore         = "com.typesafe.akka"       %% "akka-http-core"              % akkaHttpVersion
lazy val akkaPersistence      = "com.typesafe.akka"       %% "akka-persistence"            % akkaVersion
lazy val akkaPersistenceInMem = "com.github.dnvriend"     %% "akka-persistence-inmemory"   % akkaPersistenceInMemVersion
lazy val akkaHttpTestKit      = "com.typesafe.akka"       %% "akka-http-testkit"           % akkaHttpVersion
lazy val akkaTestKit          = "com.typesafe.akka"       %% "akka-testkit"                % akkaVersion
lazy val akkaSlf4j            = "com.typesafe.akka"       %% "akka-slf4j"                  % akkaVersion
lazy val catsCore             = "org.typelevel"           %% "cats-core"                   % catsVersion
lazy val circeCore            = "io.circe"                %% "circe-core"                  % circeVersion
lazy val circeJava8           = "io.circe"                %% "circe-java8"                 % circeVersion
lazy val circeRefined         = "io.circe"                %% "circe-refined"               % circeVersion
lazy val jenaArq              = "org.apache.jena"         % "jena-arq"                     % jenaVersion
lazy val mockitoCore          = "org.mockito"             % "mockito-core"                 % mockitoVersion
lazy val monixTail            = "io.monix"                %% "monix-tail"                  % monixVersion
lazy val pureconfig           = "com.github.pureconfig"   %% "pureconfig"                  % pureconfigVersion
lazy val shaclValidator       = "ch.epfl.bluebrain.nexus" %% "shacl-topquadrant-validator" % commonsVersion
lazy val shapeless            = "com.chuusai"             %% "shapeless"                   % shapelessVersion
lazy val scalaTest            = "org.scalatest"           %% "scalatest"                   % scalaTestVersion
lazy val slf4j                = "com.typesafe.akka"       %% "akka-slf4j"                  % akkaVersion

lazy val refined           = "eu.timepit" %% "refined"            % refinedVersion
lazy val refinedPureConfig = "eu.timepit" %% "refined-pureconfig" % refinedVersion

// Nexus dependency modules
lazy val commonsQueryTypes = "ch.epfl.bluebrain.nexus" %% "commons-query-types"   % commonsVersion
lazy val commonsHttp       = "ch.epfl.bluebrain.nexus" %% "commons-http"          % commonsVersion
lazy val commonsSchemas    = "ch.epfl.bluebrain.nexus" %% "commons-schemas"       % commonsVersion
lazy val commonsTest       = "ch.epfl.bluebrain.nexus" %% "commons-test"          % commonsVersion
lazy val iamClient         = "ch.epfl.bluebrain.nexus" %% "iam-client"            % iamVersion
lazy val sparqlClient      = "ch.epfl.bluebrain.nexus" %% "sparql-client"         % commonsVersion
lazy val serialization     = "ch.epfl.bluebrain.nexus" %% "service-serialization" % serviceVersion
lazy val serviceHttp       = "ch.epfl.bluebrain.nexus" %% "service-http"          % serviceVersion
lazy val serviceKamon      = "ch.epfl.bluebrain.nexus" %% "service-kamon"         % serviceVersion
lazy val sourcingAkka      = "ch.epfl.bluebrain.nexus" %% "sourcing-akka"         % sourcingVersion
lazy val serviceIndexing   = "ch.epfl.bluebrain.nexus" %% "service-indexing"      % serviceVersion
lazy val serviceKafka      = "ch.epfl.bluebrain.nexus" %% "service-kafka"         % serviceVersion
lazy val sourcingCore      = "ch.epfl.bluebrain.nexus" %% "sourcing-core"         % sourcingVersion
lazy val sourcingMem       = "ch.epfl.bluebrain.nexus" %% "sourcing-mem"          % sourcingVersion
lazy val rdfCore           = "ch.epfl.bluebrain.nexus" %% "rdf-core"              % rdfVersion
lazy val rdfCirce          = "ch.epfl.bluebrain.nexus" %% "rdf-circe"             % rdfVersion
lazy val rdfJena           = "ch.epfl.bluebrain.nexus" %% "rdf-jena"              % rdfVersion
lazy val akkaHttpCors      = "ch.megard"               %% "akka-http-cors"        % akkaHttpCorsVersion

// Projects

lazy val docs = project
  .in(file("docs"))
  .enablePlugins(ParadoxPlugin)
  .settings(common)
  .settings(
    name                         := "admin-docs",
    moduleName                   := "admin-docs",
    paradoxTheme                 := Some(builtinParadoxTheme("generic")),
    paradoxProperties in Compile ++= Map("extref.service.base_url" -> "../"),
    target in (Compile, paradox) := (resourceManaged in Compile).value / "docs",
    resourceGenerators in Compile += {
      (paradox in Compile).map { parent => (parent ** "*").get
      }.taskValue
    }
  )

lazy val refinements = project
  .in(file("modules/refined"))
  .settings(
    commonTestSettings,
    name                := "admin-refined",
    moduleName          := "admin-refined",
    coverageEnabled     := false,
    libraryDependencies ++= Seq(iamClient, rdfCore, refined, serviceHttp, commonsTest % Test, scalaTest % Test)
  )

lazy val schemas = project
  .in(file("modules/schemas"))
  .enablePlugins(WorkbenchPlugin, BuildInfoPlugin)
  .disablePlugins(ScapegoatSbtPlugin, DocumentationPlugin)
  .settings(
    common,
    name                  := "admin-schemas",
    moduleName            := "admin-schemas",
    coverageFailOnMinimum := false,
    libraryDependencies ++= Seq(
      commonsSchemas
    )
  )

lazy val ld = project
  .dependsOn(refinements, schemas)
  .in(file("modules/ld"))
  .settings(
    commonTestSettings,
    name       := "admin-ld",
    moduleName := "admin-ld",
    libraryDependencies ++= Seq(
      catsCore,
      circeCore,
      rdfCore,
      rdfCirce,
      commonsTest,
      jenaArq,
      shapeless,
      commonsHttp % Test,
      scalaTest   % Test,
      slf4j       % Test
    )
  )

lazy val query = project
  .in(file("modules/query"))
  .dependsOn(ld)
  .settings(
    commonTestSettings,
    name       := "admin-query",
    moduleName := "admin-query",
    libraryDependencies ++= Seq(
      commonsQueryTypes,
      scalaTest   % Test,
      commonsTest % Test,
      slf4j       % Test
    )
  )

lazy val core = project
  .in(file("modules/core"))
  .enablePlugins(BuildInfoPlugin)
  .dependsOn(query)
  .settings(
    common,
    commonTestSettings,
    buildInfoSettings,
    name       := "admin-core",
    moduleName := "admin-core",
    libraryDependencies ++= Seq(
      akkaPersistence,
      sparqlClient,
      circeJava8,
      circeRefined,
      commonsQueryTypes,
      pureconfig,
      refinedPureConfig,
      serialization,
      rdfJena,
      shaclValidator,
      sourcingCore,
      akkaDistributed      % Test,
      akkaHttpTestKit      % Test,
      akkaPersistenceInMem % Test,
      mockitoCore          % Test,
      scalaTest            % Test,
      sourcingMem          % Test,
      slf4j                % Test
    )
  )

lazy val service = project
  .in(file("modules/service"))
  .enablePlugins(ServicePackagingPlugin)
  .dependsOn(core % testAndCompile, docs)
  .settings(
    common,
    commonTestSettings,
    name       := "admin-service",
    moduleName := "admin-service",
    libraryDependencies ++= Seq(
      akkaDistributed,
      sparqlClient,
      serviceIndexing,
      serviceKafka,
      akkaHttpCors,
      serviceKamon,
      sourcingAkka,
      slf4j,
      akkaTestKit % Test,
      mockitoCore % Test
    )
  )

lazy val client = project
  .in(file("modules/client"))
  .dependsOn(ld)
  .settings(
    commonTestSettings,
    name       := "admin-client",
    moduleName := "admin-client",
    libraryDependencies ++= Seq(
      akkaHttpCore,
      akkaSlf4j,
      circeCore,
      circeJava8,
      circeRefined,
      monixTail,
      akkaHttpTestKit % Test,
      commonsTest     % Test,
      scalaTest       % Test
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
  .aggregate(docs, refinements, ld, query, core, service, client, schemas)

/* ********************************************************
 ******************** Grouped Settings ********************
 **********************************************************/

lazy val common = Seq(resolvers += Resolver.bintrayRepo("bogdanromanx", "maven"))

lazy val noPublish = Seq(
  publishLocal    := {},
  publish         := {},
  publishArtifact := false
)

lazy val testAndCompile = "test->test;compile->compile"

lazy val commonTestSettings = Seq(
  Test / testOptions       += Tests.Argument(TestFrameworks.ScalaTest, "-o", "-u", "target/test-reports"),
  Test / fork              := true,
  Test / parallelExecution := false // workaround for jena initialization
)

lazy val buildInfoSettings =
  Seq(buildInfoKeys := Seq[BuildInfoKey](version), buildInfoPackage := "ch.epfl.bluebrain.nexus.admin.core.config")

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

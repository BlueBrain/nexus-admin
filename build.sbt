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
val pureconfigVersion               = "0.9.0"
val refinedVersion                  = "0.8.7"
val scalaTestVersion                = "3.0.5"
val shapelessVersion                = "2.3.3"
val sourcingVersion                 = "0.10.1"

// Nexus dependency versions
val commonsVersion = "0.10.2"

// Dependency modules
lazy val akkaHttpCore = "com.typesafe.akka" %% "akka-http-core" % akkaHttpVersion
lazy val catsCore     = "org.typelevel"     %% "cats-core"      % catsVersion
lazy val circeCore    = "io.circe"          %% "circe-core"     % circeVersion
lazy val jenaArq      = "org.apache.jena"   % "jena-arq"        % jenaVersion
lazy val shapeless    = "com.chuusai"       %% "shapeless"      % shapelessVersion
lazy val scalaTest    = "org.scalatest"     %% "scalatest"      % scalaTestVersion

lazy val refined     = "eu.timepit" %% "refined"      % refinedVersion
lazy val refinedCats = "eu.timepit" %% "refined-cats" % refinedVersion // optional
lazy val refinedEval = "eu.timepit" %% "refined-eval" % refinedVersion // optional, JVM-only

// Nexus dependency modules
lazy val commonsIam  = "ch.epfl.bluebrain.nexus" %% "iam"          % commonsVersion
lazy val commonsTest = "ch.epfl.bluebrain.nexus" %% "commons-test" % commonsVersion

lazy val refinements = project
  .in(file("modules/refined"))
  .settings(
    name            := "admin-refined",
    moduleName      := "admin-refined",
    coverageEnabled := false,
    libraryDependencies ++= Seq(
      akkaHttpCore,
      refined,
      refinedCats,
      refinedEval
    )
  )
// Projects
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

lazy val root = project
  .in(file("."))
  .settings(noPublish)
  .settings(
    name                  := "admin",
    moduleName            := "admin",
    coverageFailOnMinimum := false
  )
  .aggregate(ld, refinements)

/* ********************************************************
 ******************** Grouped Settings ********************
 **********************************************************/

lazy val noPublish = Seq(
  publishLocal    := {},
  publish         := {},
  publishArtifact := false,
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
      Developer("wwajerowicz", "Wojtek Wajerowicz", "noreply@epfl.ch", url("https://bluebrain.epfl.ch/")),
    ),
    // These are the sbt-release-early settings to configure
    releaseEarlyWith              := BintrayPublisher,
    releaseEarlyNoGpg             := true,
    releaseEarlyEnableSyncToMaven := false,
  ))

addCommandAlias("review", ";clean;scalafmtSbtCheck;coverage;scapegoat;test;coverageReport;coverageAggregate")

enablePlugins(GatlingPlugin)

scalaVersion := "2.13.16"

scalacOptions := Seq(
  "-encoding", "UTF-8",
  "-release:8",
  "-deprecation",
  "-feature",
  "-unchecked",
  "-language:implicitConversions",
  "-language:postfixOps"
)

val gatlingVersion = "3.13.5"

libraryDependencies ++= Seq(
  // Core & HTTP para gatling:test
  "io.gatling" % "gatling-core" % gatlingVersion % "test,it",         // :contentReference[oaicite:0]{index=0}
  "io.gatling" % "gatling-http" % gatlingVersion % "test,it",         // :contentReference[oaicite:1]{index=1}

  // Reporting (Highcharts) — coincide con la versión core/http
  "io.gatling.highcharts" % "gatling-charts-highcharts" % gatlingVersion % "test,it",  // :contentReference[oaicite:2]{index=2}

  // Framework de test (assertions, DSL, etc.)
  "io.gatling" % "gatling-test-framework" % gatlingVersion % "test,it"               // :contentReference[oaicite:3]{index=3}

  // JSON
  ,
  "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.18.2"
)

ThisBuild / fork := true

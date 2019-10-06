// *****************************************************************************
// Projects
// *****************************************************************************

lazy val cheese =
  project
    .in(file("."))
    .enablePlugins(AutomateHeaderPlugin)
    .settings(settings)
    .settings(
      libraryDependencies ++= Seq(
        library.kafkaClients,
        library.kafkaStreams,
        library.kafkaStreamsScala,
        library.serdeTools,
        library.logback,
        library.scalaLogging,
        library.kafkaStreamsTestUtils % Test,
        library.scalaCheck % Test,
        library.scalaTest  % Test,
      )
    )

// *****************************************************************************
// Library dependencies
// *****************************************************************************

lazy val library =
  new {
    object Version {
      val scalaCheck = "1.14.2"
      val scalaTest  = "3.0.8"
    }

    val kafkaClients =  "org.apache.kafka" % "kafka-clients" % "2.3.0"
    val kafkaStreams = "org.apache.kafka" % "kafka-streams" % "2.3.0"
    val kafkaStreamsScala = "org.apache.kafka" %% "kafka-streams-scala" % "2.3.0"

    // serializers & converters
    val serdeTools =  "io.confluent" % "kafka-serde-tools-package" % "5.3.1"

    val logback ="ch.qos.logback" % "logback-classic" % "1.2.3"
    val scalaLogging = "com.typesafe.scala-logging" %% "scala-logging" % "3.9.2"

    val kafkaStreamsTestUtils = "org.apache.kafka" % "kafka-streams-test-utils" % "2.3.0"

    val scalaCheck = "org.scalacheck" %% "scalacheck" % Version.scalaCheck
    val scalaTest  = "org.scalatest"  %% "scalatest"  % Version.scalaTest
  }

// *****************************************************************************
// Settings
// *****************************************************************************

lazy val settings =
  commonSettings ++
  scalafmtSettings

lazy val commonSettings =
  Seq(
    scalaVersion := "2.12.10",
    organization := "default",
    organizationName := "konstantin.silin",
    startYear := Some(2019),
    licenses += ("BSD-3-Clause", url("https://opensource.org/licenses/BSD-3-Clause")),
    scalacOptions ++= Seq(
      "-unchecked",
      "-deprecation",
      "-language:_",
      "-target:jvm-1.8",
      "-encoding", "UTF-8",
      "-Ywarn-unused:imports",
    ),
    resolvers += "confluent" at "https://packages.confluent.io/maven/",
    Compile / unmanagedSourceDirectories := Seq((Compile / scalaSource).value),
    Test / unmanagedSourceDirectories := Seq((Test / scalaSource).value),
)

lazy val scalafmtSettings =
  Seq(
    scalafmtOnCompile := true,
  )

enablePlugins(DockerComposePlugin)
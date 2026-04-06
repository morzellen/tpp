ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := "3.8.3"
ThisBuild / organization := "com.tpp"

// Версии зависимостей
val zioVersion = "2.1.1"
val zioConfigVersion = "4.0.2"
val zioKafkaVersion = "2.4.0"
val zioLoggingVersion = "2.2.2"
val http4sVersion = "1.0.0-M41"
val doobieVersion = "1.0.0-RC5"
val circeVersion = "0.14.6"
val scalaTestVersion = "3.2.18"
val scalaCheckVersion = "1.17.0"

lazy val root = (project in file("."))
  .settings(
    name := "tpp",

    // Scalac options
    scalacOptions ++= Seq(
      "-deprecation",
      "-feature",
      "-unchecked"
      // "-Xfatal-warnings" — включим когда код будет стабилен
    ),

    // Зависимости
    libraryDependencies ++= Seq(
      // ZIO Core
      "dev.zio" %% "zio" % zioVersion,

      // ZIO Config
      "dev.zio" %% "zio-config" % zioConfigVersion,
      "dev.zio" %% "zio-config-magnolia" % zioConfigVersion,
      "dev.zio" %% "zio-config-typesafe" % zioConfigVersion,

      // ZIO Logging
      "dev.zio" %% "zio-logging" % zioLoggingVersion,
      "dev.zio" %% "zio-logging-slf4j" % zioLoggingVersion,

      // http4s (HTTP framework)
      "org.http4s" %% "http4s-ember-server" % http4sVersion,
      "org.http4s" %% "http4s-dsl" % http4sVersion,
      "org.http4s" %% "http4s-circe" % http4sVersion,

      // Doobie (PostgreSQL)
      "org.tpolecat" %% "doobie-core" % doobieVersion,
      "org.tpolecat" %% "doobie-postgres" % doobieVersion,
      "org.tpolecat" %% "doobie-hikari" % doobieVersion,

      // Circe (JSON)
      "io.circe" %% "circe-core" % circeVersion,
      "io.circe" %% "circe-generic" % circeVersion,
      "io.circe" %% "circe-parser" % circeVersion,

      // ZIO Kafka
      "dev.zio" %% "zio-kafka" % zioKafkaVersion,

      // Testing
      "org.scalatest" %% "scalatest" % scalaTestVersion % Test,
      "org.scalacheck" %% "scalacheck" % scalaCheckVersion % Test,
      "dev.zio" %% "zio-test" % zioVersion % Test,
      "dev.zio" %% "zio-test-sbt" % zioVersion % Test
    ),

    // Test settings
    testFrameworks ++= Seq(
      new TestFramework("org.scalatest.tools.Framework"),
      new TestFramework("zio.test.sbt.ZTestFramework")
    ),

    // Fork tests for isolation
    Test / fork := true
  )

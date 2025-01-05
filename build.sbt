val scalusVersion = "0.8.4"

// Latest Scala 3 LTS version
ThisBuild / scalaVersion := "3.3.4"

ThisBuild / scalacOptions ++= Seq("-feature", "-deprecation", "-unchecked")

// Add the Scalus compiler plugin
addCompilerPlugin("org.scalus" %% "scalus-plugin" % scalusVersion)

// Test dependencies
ThisBuild / testFrameworks += new TestFramework("munit.Framework")

// Main application
lazy val core = (project in file("."))
    .settings(
      libraryDependencies ++= Seq(
        // Scalus
        "org.scalus" % "scalus_3" % scalusVersion,
        "org.scalus" % "scalus-bloxbean-cardano-client-lib_3" % scalusVersion,
        // Cardano Client library
        "com.bloxbean.cardano" % "cardano-client-lib" % "0.6.3",
        "com.bloxbean.cardano" % "cardano-client-backend-blockfrost" % "0.6.3",
        // Tapir for API definition
        "com.softwaremill.sttp.tapir" %% "tapir-netty-server-sync" % "1.11.11",
        "com.softwaremill.sttp.tapir" %% "tapir-swagger-ui-bundle" % "1.11.11",
        // Argument parsing
        "com.monovore" %% "decline" % "2.5.0",
        "org.slf4j" % "slf4j-simple" % "2.0.16"
      ),
      libraryDependencies ++= Seq(
        "org.scalameta" %% "munit" % "1.0.3" % Test,
        "org.scalameta" %% "munit-scalacheck" % "1.0.0" % Test,
        "org.scalacheck" %% "scalacheck" % "1.18.1" % Test
      )
    )

// Integration tests
lazy val integration = (project in file("integration"))
    .dependsOn(core) // your current subproject
    .settings(
      publish / skip := true,
      // test dependencies
      libraryDependencies ++= Seq(
        "org.scalameta" %% "munit" % "1.0.3" % Test
      )
    )

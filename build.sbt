val scalusVersion = "0.10.1+228-294456c3-SNAPSHOT"
// val scalusVersion = "0.10.1+215-b9f4095d+20250707-1958-SNAPSHOT"
//val scalusVersion = "0.10.1"
resolvers += Resolver.sonatypeCentralSnapshots

// Latest Scala 3 LTS version
ThisBuild / scalaVersion := "3.3.6"

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
        "org.scalus" %% "scalus" % scalusVersion,
        "org.scalus" %% "scalus-testkit" % scalusVersion,
        "org.scalus" %% "scalus-bloxbean-cardano-client-lib" % scalusVersion,
        // Cardano Client library
        "com.bloxbean.cardano" % "cardano-client-lib" % "0.6.6",
        "com.bloxbean.cardano" % "cardano-client-backend-blockfrost" % "0.6.6",
        // Tapir for API definition
        "com.softwaremill.sttp.tapir" %% "tapir-netty-server-sync" % "1.11.35",
        "com.softwaremill.sttp.tapir" %% "tapir-swagger-ui-bundle" % "1.11.35",
        // Argument parsing
        "com.monovore" %% "decline" % "2.5.0",
        "org.slf4j" % "slf4j-simple" % "2.0.17"
      ),
      libraryDependencies += "com.lihaoyi" %% "requests" % "0.8.0",
      libraryDependencies ++= Seq(
        "org.scalameta" %% "munit" % "1.1.1" % Test,
        "org.scalameta" %% "munit-scalacheck" % "1.1.0" % Test,
        "org.scalacheck" %% "scalacheck" % "1.18.1" % Test
      )
    )

// Integration tests
lazy val integration = (project in file("integration"))
    .dependsOn(core) // your current subproject
    .settings(
      publish / skip := true,
      // test dependencies
      libraryDependencies += "com.lihaoyi" %% "requests" % "0.8.0",
      libraryDependencies ++= Seq(
        "org.scalameta" %% "munit" % "1.1.1" % Test,
        "org.scalameta" %% "munit-scalacheck" % "1.1.0" % Test,
        "org.scalacheck" %% "scalacheck" % "1.18.1" % Test
      )
    )

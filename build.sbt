import complete.DefaultParsers._
import sbtrelease.ReleaseStateTransformations._

val Http4sVersion = "1.0.0-M36"
val CirceVersion = "0.14.1"
val MunitVersion = "0.7.29"
val LogbackVersion = "1.2.6"
val MunitCatsEffectVersion = "1.0.7"
val SlickVersion = "3.4.1"

lazy val root = (project in file("."))
  .settings(
    organization := "com.marmaladesky",
    name := "condoit",
    scalaVersion := "2.13.8",
    scalacOptions ++= Seq(
      "-Ymacro-annotations",
      """-Wconf:src=.*gen\/Tables\.scala:silent"""
    ),
    libraryDependencies ++= Seq(
      // Core
      "org.http4s"      %% "http4s-blaze-server"  % Http4sVersion,
      "org.http4s"      %% "http4s-blaze-client"  % Http4sVersion,
      "org.http4s"      %% "http4s-circe"         % Http4sVersion,
      "org.http4s"      %% "http4s-dsl"           % Http4sVersion,
      "io.circe"        %% "circe-generic"        % CirceVersion,
      "io.circe"        %% "circe-generic-extras" % CirceVersion,
      "ch.qos.logback"  %  "logback-classic"      % LogbackVersion,
      "org.scalameta"   %% "svm-subs"             % "20.2.0",

      // Third-party misc
      "com.github.jwt-scala"  %% "jwt-core"  % "9.0.2",
      "com.github.jwt-scala"  %% "jwt-circe" % "9.0.2",
      "com.github.slugify"    %  "slugify"   % "2.5",

      // Slick
      "com.typesafe.slick"  %% "slick" % SlickVersion,
      "com.typesafe.slick"  %% "slick-hikaricp" % SlickVersion,
      "com.github.tminglei" %% "slick-pg" % "0.21.0",
      "com.typesafe.slick"  %% "slick-codegen" % SlickVersion,

      // Logging
      "com.typesafe.scala-logging" %% "scala-logging" % "3.9.4",

      // Config
      "com.github.pureconfig"   %% "pureconfig" % "0.16.0",

      // Test
      "org.scalameta"  %% "munit"               % MunitVersion           % Test,
      "org.typelevel"  %% "munit-cats-effect-3" % MunitCatsEffectVersion % Test,
      "org.mockito"    %% "mockito-scala"       % "1.16.49"              % Test,
      "io.circe"       %% "circe-optics"        % CirceVersion           % Test
    ),
    addCompilerPlugin("org.typelevel" %% "kind-projector"     % "0.13.2" cross CrossVersion.full),
    addCompilerPlugin("com.olegpy"    %% "better-monadic-for" % "0.3.1"),
    releaseProcess := Seq[ReleaseStep](
      checkSnapshotDependencies,
      inquireVersions,
      runClean,
      runTest,
      setReleaseVersion,
      commitReleaseVersion,
      tagRelease,
      setNextVersion,
      commitNextVersion
    ),
    testFrameworks += new TestFramework("munit.Framework"),
    slickPgGen := {
      val args: Seq[String] = spaceDelimited("<arg>").parsed
      val host = args(0)
      val port = args(1)
      val dbName = args(2)
      val schema = args(3)
      val user = args(4)
      val password = args(5)

      val dir = (Compile / scalaSource).value
      val pkg = "com.marmaladesky.realworld.db.gen"
      val profile = "com.marmaladesky.realworld.db.DbProfile"

      val outputDir = pkg.split(".").foldLeft(dir)((f, d) => f / d)

      val cp = (Compile / fullClasspath).value
      val s = streams.value

      runner.value.run("com.marmaladesky.realworld.db.gen.AdvancedPgGenerator",
        cp.files,
        Array(host, port, dbName, schema, user, password, "./src/main/scala", pkg, profile, "Tables"), s.log
      ).failed foreach (sys error _.getMessage)

      val file = outputDir / pkg / "Tables.scala"

      Seq(file)
    }
  )

lazy val slickPgGen = inputKey[Seq[File]]("Generate Tables.scala")
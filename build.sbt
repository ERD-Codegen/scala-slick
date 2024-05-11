import complete.DefaultParsers._
import sbtrelease.ReleaseStateTransformations._

val Http4sVersion = "1.0.0-M41"
val Http4sBlazeVersion = "1.0.0-M40"
val CirceVersion = "0.14.1"
val MunitVersion = "0.7.29"
val LogbackVersion = "1.5.6"
val MunitCatsEffectVersion = "1.0.7"
val SlickVersion = "3.5.1"
val SlickPgVersion = "0.22.1"
val SvmSubsVersion = "20.2.0"
val JwtScalaVersion = "9.0.2"
val SlugifyVersion = "3.0.6"
val ScalaLoggingVersion = "3.9.5"
val Log4CatsVersion = "2.7.0"
val PureConfigVersion = "0.17.6"
val MockitoScalaVersion = "1.17.31"
val TypelevelKindProjectorVersion = "0.13.3"
val BetterMonadicForVersion = "0.3.1"

lazy val root = (project in file("."))
  .settings(
    organization := "com.marmaladesky",
    name := "condoit",
    scalaVersion := "2.13.14",
    scalacOptions ++= Seq(
      "-Ymacro-annotations",
      """-Wconf:src=.*gen\/Tables\.scala:silent"""
    ),
    libraryDependencies ++= Seq(
      // Core
      "org.http4s" %% "http4s-core" % Http4sVersion,
      "org.http4s" %% "http4s-blaze-server" % Http4sBlazeVersion,
      "org.http4s" %% "http4s-blaze-client" % Http4sBlazeVersion,
      "org.http4s" %% "http4s-circe" % Http4sVersion,
      "org.http4s" %% "http4s-dsl" % Http4sVersion,
      "io.circe" %% "circe-generic" % CirceVersion,
      "io.circe" %% "circe-generic-extras" % CirceVersion,
      "ch.qos.logback" % "logback-classic" % LogbackVersion,
      "org.scalameta" %% "svm-subs" % SvmSubsVersion,

      // Third-party misc
      "com.github.jwt-scala" %% "jwt-core" % JwtScalaVersion,
      "com.github.jwt-scala" %% "jwt-circe" % JwtScalaVersion,
      "com.github.slugify" % "slugify" % SlugifyVersion,

      // Slick
      "com.typesafe.slick" %% "slick" % SlickVersion,
      "com.typesafe.slick" %% "slick-hikaricp" % SlickVersion,
      "com.github.tminglei" %% "slick-pg" % SlickPgVersion,
      "com.typesafe.slick" %% "slick-codegen" % SlickVersion,

      // Logging
      "com.typesafe.scala-logging" %% "scala-logging" % ScalaLoggingVersion,
      "org.typelevel" %% "log4cats-slf4j" % Log4CatsVersion,

      // Config
      "com.github.pureconfig" %% "pureconfig" % PureConfigVersion,

      // Test
      "org.scalameta" %% "munit" % MunitVersion % Test,
      "org.typelevel" %% "munit-cats-effect-3" % MunitCatsEffectVersion % Test,
      "org.mockito" %% "mockito-scala" % MockitoScalaVersion % Test,
      "io.circe" %% "circe-optics" % CirceVersion % Test
    ),
    addCompilerPlugin("org.typelevel" %% "kind-projector" % TypelevelKindProjectorVersion cross CrossVersion.full),
    addCompilerPlugin("com.olegpy" %% "better-monadic-for" % BetterMonadicForVersion),
    Compile / run / fork := true,
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

      runner.value
        .run(
          "com.marmaladesky.realworld.db.gen.AdvancedPgGenerator",
          cp.files,
          Array(host, port, dbName, schema, user, password, "./src/main/scala", pkg, profile, "Tables"),
          s.log
        )
        .failed foreach (sys error _.getMessage)

      val file = outputDir / pkg / "Tables.scala"

      Seq(file)
    }
  )

lazy val slickPgGen = inputKey[Seq[File]]("Generate Tables.scala")

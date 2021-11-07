package com.marmaladesky.realworld

import cats.effect.{ExitCode, IO, IOApp}
import com.marmaladesky.realworld.db.AppDb

object Main extends IOApp with AppDb {

  def run(args: List[String]): IO[ExitCode] = {
    val config = AppConfig.impl

    database[IO](config).use { db =>
      RealWorldServer.stream[IO](db, config).compile.drain.as(ExitCode.Success)
    }
  }

}

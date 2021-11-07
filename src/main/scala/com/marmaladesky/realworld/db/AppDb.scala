package com.marmaladesky.realworld.db

import cats.effect.Resource
import com.marmaladesky.realworld.AppConfig
import slick.jdbc.JdbcBackend.Database

trait AppDb {

  def database[F[_]](config: AppConfig): Resource[F, Database] = {
    Resource.pure(Database.forConfig("", config = config.db))
  }

}

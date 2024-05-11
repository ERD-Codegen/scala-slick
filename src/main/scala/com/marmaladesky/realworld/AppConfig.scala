package com.marmaladesky.realworld

import com.marmaladesky.realworld.AppConfig.Http
import com.typesafe.config.Config
import pureconfig.ConfigSource
import pureconfig.generic.auto._

case class AppConfig(
    http: Http,
    db: Config
)

object AppConfig {

  case class Http(host: String, port: Int)

  def impl: AppConfig = ConfigSource.default.loadOrThrow[AppConfig]

}

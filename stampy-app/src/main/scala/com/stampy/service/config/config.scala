package com.stampy.service.config

import com.stampy.service.modules.org.RegisterOrgConfig
import com.typesafe.config.Config
import io.circe.generic.auto._
import io.circe.config.syntax._
import io.circe.config.parser

import scala.util.Try

case class StampyAppConfig(server: ServerConfig, postgres: Config, registerOrg: RegisterOrgConfig)
case class ServerConfig(port: Int)

object StampyAppConfig {
  def read: Try[StampyAppConfig] =
    parser.decode[StampyAppConfig]().toTry

  def read(config: Config): Try[StampyAppConfig] =
    parser.decode[StampyAppConfig](config).toTry
}

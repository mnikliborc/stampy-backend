package com.stampy.service.tapir

import com.stampy.service.util.Logger
import sttp.tapir.server.ServerEndpoint

import scala.concurrent.Future

trait TapirRoute extends Http with Logger {
  def spec: ServerEndpoint[_, _, _, Nothing, Future]
}
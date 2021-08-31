package com.stampy.service.infrastructure

import com.stampy.service.util.Logger
import io.vertx.core.Handler
import io.vertx.scala.core.net.SocketAddress
import io.vertx.scala.ext.web.RoutingContext

object AccessLogger extends Logger {
  def create(): Handler[RoutingContext] = { ctx =>
    val start = System.currentTimeMillis
    ctx.addBodyEndHandler(_ => accessLog(ctx, start))
    ctx.next()
  }

  private def accessLog(ctx: RoutingContext, timestamp: Long): Unit = {
    val remoteClient = getClientAddress(ctx.request.remoteAddress)
    val method = ctx.request.method
    val status = ctx.response.getStatusCode
    val uri = ctx.request.uri
    val accessLogMessage = s"[$remoteClient] $method $uri $status ${System.currentTimeMillis() - timestamp}[ms]"
    log.info(accessLogMessage)
  }

  private def getClientAddress(inetSocketAddress: SocketAddress): String = {
    if (inetSocketAddress == null) return "-"
    inetSocketAddress.host
  }
}

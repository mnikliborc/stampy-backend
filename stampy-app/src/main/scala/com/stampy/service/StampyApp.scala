package com.stampy.service

import java.time.Clock
import java.util.concurrent.Executors

import com.stampy.service.config.StampyAppConfig
import com.stampy.service.db.Postgres
import com.stampy.service.db.Postgres.PostgresCtx
import com.stampy.service.modules.security.ApiKeyServiceImpl
import com.stampy.service.modules.{org, stamp, user, packet}
import com.stampy.service.tapir.{Auth, TapirRoute, TapirRouter}
import io.vertx.scala.core.Vertx

import scala.concurrent.{ExecutionContext, Future}


object StampyApp extends App {
  val config: StampyAppConfig = StampyAppConfig.read.get
  start(config)(Clock.systemUTC())

  implicit lazy val ec = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(Runtime.getRuntime.availableProcessors))

  def start(config: StampyAppConfig)(implicit clock: Clock): Future[Vertx] = {
    Postgres.migrate(config.postgres).get
    val ctx: PostgresCtx = Postgres.ctx(config.postgres).get
    val auth: Auth = new Auth(new ApiKeyServiceImpl(ctx))

    val routes: List[TapirRoute] =
      List(
        new org.RegisterRoute(ctx, auth, config.registerOrg),
        new org.OwnedOrgsRoute(ctx, auth),
        new org.CreateOrgInviteRoute(ctx, auth),
        new org.GetOrgStatusRoute(ctx, auth),
        new org.AcceptOrgInviteRoute(ctx, auth),
        new org.RejectOrgInviteRoute(ctx, auth),
        new org.RemoveMemberRoute(ctx, auth),
        new org.SelectOrgRoute(ctx, auth),

        new stamp.RegisterRoute(ctx, auth),
        new stamp.ClaimRoute(ctx, auth),

        new packet.BuyRoute(ctx, auth),
        new packet.StatusRoute(ctx, auth),

        new user.RegisterRoute(ctx),
        new user.LoginRoute(ctx),
        new user.GetSelfRoute(ctx, auth),
        new user.ListOrgInvitesRoute(ctx, auth),
        new user.ListOrgMembershipRoute(ctx, auth),
      )

    val vertx = Vertx.vertx()
    val server = vertx.createHttpServer()
    val router = TapirRouter(vertx, routes)
    server.requestHandler(router).listenFuture(config.server.port).map(_ => vertx)
  }
}


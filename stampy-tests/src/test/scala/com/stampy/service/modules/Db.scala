package com.stampy.service.modules

import com.stampy.service.db.Postgres
import com.stampy.service.db.Postgres.PostgresCtx
import com.stampy.service.modules.org.{CardTemplateModel, MembershipModel, OrgInviteModel, OrgModel}
import com.stampy.service.modules.packet.StampPacketModel
import com.stampy.service.modules.stamp.StampModel
import com.stampy.service.modules.user.UserModel
import com.typesafe.config.Config

import scala.concurrent.Await
import scala.concurrent.duration._

trait Db extends Postgres
  with CardTemplateModel with MembershipModel with OrgInviteModel with OrgModel
  with StampModel with StampPacketModel
  with UserModel {

  def postgresConfig: Config

  implicit val ec = scala.concurrent.ExecutionContext.global
  lazy val ctx: PostgresCtx = Postgres.ctx(postgresConfig).get

  implicit class ExecDb[+T, -E <: ctx.Effect](io: ctx.IO[T, E]) {
    def exec(): T = Await.result(ctx.performIO(io), 1.second)
  }
}

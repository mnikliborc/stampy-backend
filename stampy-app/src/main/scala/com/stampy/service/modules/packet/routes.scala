package com.stampy.service.modules.packet

import java.time.Clock

import com.stampy.service.db.Postgres
import com.stampy.service.db.Postgres.PostgresCtx
import com.stampy.service.{Fail, domain}
import com.stampy.service.domain.{Permission, StampPacket}
import com.stampy.service.infrastructure.Codecs._
import com.stampy.service.modules.org.OrgModel
import com.stampy.service.modules.org.endpoints.selectOrg
import com.stampy.service.modules.packet.models.Status_OUT
import com.stampy.service.tapir.{Auth, Error_OUT, Http, TapirRoute}
import com.stampy.service.util.{DefaultIdGenerator, Id}
import sttp.model.StatusCode
import sttp.tapir.server.ServerEndpoint

import scala.concurrent.{ExecutionContext, Future}

class BuyRoute(val ctx: PostgresCtx, auth: Auth)(implicit val ec: ExecutionContext, clock: Clock) extends TapirRoute with Postgres with StampPacketModel with OrgModel  {

  override def spec: ServerEndpoint[Id[domain.ApiKey], (StatusCode, Error_OUT), Unit, Nothing, Future] =
    endpoints.buy
      .serverLogicPart(auth.authzWithOrg(Permission.Owner))
      .andThen { case ((userId, orgId), ()) =>
        val program: Future[Unit] =
          for {
            orgOpt <- ctx.performIO(orgModel.find(orgId))
            _      <- orgOpt.toFail(Fail.NotFound("org_not_found"))
            packet  = StampPacket(DefaultIdGenerator.nextId(), orgId, 10, 0, clock.instant(), clock.instant().plusSeconds(600))
            _      <- ctx.performIO(stampPacketModel.create(packet))
          } yield ()

        program.toOut
      }
}

class StatusRoute(val ctx: PostgresCtx, auth: Auth)(implicit val ec: ExecutionContext, clock: Clock) extends TapirRoute with Postgres with StampPacketModel with OrgModel  {

  override def spec =
    endpoints.status
      .serverLogicPart(auth.authzWithOrg(Permission.Owner))
      .andThen { case ((userId, orgId), ()) =>
        val program: Future[Status_OUT] =
          for {
            orgOpt <- ctx.performIO(orgModel.find(orgId))
            _      <- orgOpt.toFail(Fail.NotFound("org_not_found"))
            packets <- ctx.performIO(stampPacketModel.getAll(orgId))
            (active, inactive) = packets.partition(p => p.validUntil.isAfter(clock.instant()) && p.size > p.used)
            availableStamps = StampPacket.availableStamps(active)
          } yield Status_OUT(active.sortBy(_.createdOn), inactive.sortBy(_.createdOn), availableStamps)

        program.toOut
      }
}

object endpoints extends Http {
  val StampPath = "packet"

  val buy =
    secureEndpoint.post // TODO this is dummy endpoint
      .in(StampPath / "buy")
      .tag("packet")
      .description(
        s"""
           |Gives stamp packet to the org. This is dummy endpoint that creates packet with 10 stamps valid 10 minutes.
           |Requires 'Authorization: Bearer {apiKey}' header where 'apiKey' obtained from '${selectOrg.info.name.get}' route.
           |""".stripMargin)

  val status =
    secureEndpoint.get
      .in(StampPath / "status")
      .out(jsonBody[Status_OUT])
      .tag("packet")
      .description(
        s"""
           |Returns stamp packets status of the org.
           |Requires 'Authorization: Bearer {apiKey}' header where 'apiKey' obtained from '${selectOrg.info.name.get}' route.
           |""".stripMargin)
}

object models {
  case class Status_OUT(active: List[StampPacket], inactive: List[StampPacket], availableStamps: Int)
}
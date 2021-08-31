package com.stampy.service.modules.stamp

import java.time.Instant

import com.stampy.service.config.StampyAppConfig
import com.stampy.service.domain.StampPacket
import com.stampy.service.modules.E2E
import com.stampy.service.modules.org.RegisterOrgConfig
import com.stampy.service.util.{DefaultIdGenerator, Id}
import org.junit.Test
import org.scalatest.MustMatchers
import sttp.model.StatusCode

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

class StampRoutesSpec extends E2E with MustMatchers {
  private val registerStamp: models.Stamp_Register_IN = stamp.models.Stamp_Register_IN(Id("test"), Instant.now())
  private val registerOrgConfig = RegisterOrgConfig(10, 10)

  override def overrideConfig(config: StampyAppConfig): StampyAppConfig =
    config.copy(registerOrg = registerOrgConfig)

  @Test
  def shouldRegisterStamp(): Unit = {
    val wWorker = withWorker

    val registerResp = stamp.endpoints.register.send(wWorker.apiKey, registerStamp)
    registerResp.code must be(StatusCode.Ok)
    registerResp.body.get.stampsAvailable must be(registerOrgConfig.freeStamps - 1)
  }

  @Test
  def shouldRejectRegisterWhenNoApiKey(): Unit = {
    stamp.endpoints.register.send(Id("invalid"), registerStamp).code must be(StatusCode.Unauthorized)
  }

  @Test
  def shouldRejectRegisterWhenNoMoreStampsAvailable(): Unit = {
    val wWorker = withWorker

    (0 until registerOrgConfig.freeStamps).foreach(i => stamp.endpoints.register.send(wWorker.apiKey, registerStamp.copy(stampId = Id(i.toString))).code must be(StatusCode.Ok))
    stamp.endpoints.register.send(wWorker.apiKey, registerStamp).code must be(StatusCode.Conflict)
  }

  @Test
  def shouldRejectRegisterWhenNoMoreStampsAvailableButAcceptWhenPacketBought(): Unit = {
    val (wWorker, wOwner) = withWorkerAndOwner

    (0 until registerOrgConfig.freeStamps).foreach(i => stamp.endpoints.register.send(wWorker.apiKey, registerStamp.copy(stampId = Id(i.toString))).code must be(StatusCode.Ok))
    stamp.endpoints.register.send(wWorker.apiKey, registerStamp).code must be(StatusCode.Conflict)

    stamp.endpoints.buy.send(wOwner.apiKey).code must be(StatusCode.Ok)
    val registerResp = stamp.endpoints.register.send(wWorker.apiKey, registerStamp)
    registerResp.code must be(StatusCode.Ok)
    registerResp.body.get.stampsAvailable must be(registerOrgConfig.freeStamps - 1)
  }

  @Test
  def shouldRejectRegisterWhenPacketExpired(): Unit = {
    val (wWorker, wOwner) = withWorkerAndOwner

    import ctx._
    val packet = StampPacket(DefaultIdGenerator.nextId(), wOwner.orgId, 10, 0, Instant.now(), Instant.now().minusSeconds(100))
    ctx.runIO(stampPacketModel.packets.delete).exec()
    ctx.runIO(stampPacketModel.packets.insert(lift(packet))).exec()

    stamp.endpoints.register.send(wWorker.apiKey, registerStamp).code must be(StatusCode.Conflict)
  }

  @Test
  def shouldRejectClaimWhenNoApiKey(): Unit = {
    stamp.endpoints.claim.send(Id("invalid"), models.Stamp_Claim_IN(List(Id("stamp-id")))).code must be(StatusCode.Unauthorized)
  }

  @Test
  def shouldClaimSuccessfully(): Unit = {
    val wWorker = withWorker

    val registers = (0 until 10).map { i =>
      registerStamp.copy(stampId = Id(i.toString))
    }
    registers.foreach(stamp.endpoints.register.send(wWorker.apiKey, _))
    stamp.endpoints.claim.send(wWorker.apiKey, models.Stamp_Claim_IN(registers.map(_.stampId).toList)).code must be(StatusCode.Ok)
    stamp.endpoints.claim.send(wWorker.apiKey, models.Stamp_Claim_IN(registers.map(_.stampId).toList)).code must be(StatusCode.BadRequest)
  }

  @Test
  def shouldRejectExpiredCard(): Unit = {
    val wWorker = withWorker

    val registers = (0 until 10).map { i =>
      registerStamp.copy(stampId = Id(i.toString), createdOn = Instant.now().minusSeconds(3600 * 24 * 30 * 24))
    }
    registers.foreach(stamp.endpoints.register.send(wWorker.apiKey, _))
    stamp.endpoints.claim.send(wWorker.apiKey, models.Stamp_Claim_IN(registers.map(_.stampId).toList)).code must be(StatusCode.BadRequest)
  }

  private def withWorker = withWorkerAndOwner._1

  private def withWorkerAndOwner = {
    val wWorker = withUser()
    val wOwner = withUserAndOrgSelected()
    val orgId = wOwner.orgId

    org.endpoints.createOrgInvite.send(wOwner.apiKey, org.models.Org_Invite_IN(wWorker.user.email.value))
    org.endpoints.acceptOrgInvite.send(wWorker.apiKey, orgId)
    val orgApiKey = org.endpoints.selectOrg.send(wWorker.apiKey, orgId).body.get.apiKey
    (wWorker.copy(apiKey = orgApiKey), wOwner)
  }
}

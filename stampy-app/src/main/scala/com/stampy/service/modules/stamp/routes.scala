package com.stampy.service.modules.stamp

import java.time.Clock
import java.util.Calendar

import com.stampy.service.db.Postgres
import com.stampy.service.db.Postgres.PostgresCtx
import com.stampy.service.domain._
import com.stampy.service.infrastructure.Codecs._
import com.stampy.service.modules.org.endpoints.selectOrg
import com.stampy.service.modules.org.{CardTemplateModel, OrgModel}
import com.stampy.service.modules.packet.StampPacketModel
import com.stampy.service.modules.stamp.models._
import com.stampy.service.tapir.{Auth, Error_OUT, Http, TapirRoute}
import com.stampy.service.util.Id
import com.stampy.service.{Fail, domain}
import io.circe.syntax._
import sttp.model.StatusCode
import sttp.tapir.server.ServerEndpoint

import scala.concurrent.{ExecutionContext, Future}

class RegisterRoute(val ctx: PostgresCtx, auth: Auth)(implicit val ec: ExecutionContext, clock: Clock) extends TapirRoute with Postgres with StampModel with StampPacketModel with OrgModel with CardTemplateModel {

  override def spec: ServerEndpoint[(Id[domain.ApiKey], Stamp_Register_IN), (StatusCode, Error_OUT), Stamp_Register_OUT, Nothing, Future] =
    endpoints.register
      .serverLogicPart(auth.authzWithOrg(Permission.Member))
      .andThen { case ((userId, orgId), body) =>
        val program: Future[Stamp_Register_OUT] =
          for {
            orgOpt          <- ctx.performIO(orgModel.find(orgId))
            _               <- orgOpt.toFail(Fail.NotFound("org_not_found"))
            cardTemplateOpt <- ctx.performIO(cardTemplateModel.findLatest(orgId))
            cardTemplate    <- cardTemplateOpt.toFail(Fail.NotFound("card_template_not_found"))
            packets         <- ctx.performIO(stampPacketModel.getAllActive(orgId, clock.instant()))
            packet          <- findOldestActiveStampPacket(packets).toFail(Fail.Conflict("stamps_depleted"))
            registeredOn     = clock.instant()
            stamp            = Stamp(body.stampId, userId, orgId, cardTemplate.version, registeredOn, registeredOn, None)
            _               <- ctx.performTransactIO(
                                  for {
                                    _ <- stampPacketModel.incrementUsedStamps(packet)
                                    _ <- stampModel.create(stamp)
                                  } yield ()
                                )
          } yield Stamp_Register_OUT(cardTemplate.version, StampPacket.availableStamps(packets) - 1)

        program.toOut
      }

  def findOldestActiveStampPacket(packets: List[StampPacket]): Option[Id[StampPacket]] =
    packets.sortBy(_.createdOn).reverse.headOption.map(_.id)
}

class ClaimRoute(val ctx: PostgresCtx, auth: Auth)(implicit val ec: ExecutionContext, clock: Clock) extends TapirRoute with Postgres with StampModel with OrgModel with CardTemplateModel {
  import ClaimValidator._

  override def spec =
    endpoints.claim
    .serverLogicPart(auth.authzWithOrg(Permission.Member))
    .andThen { case ((userId, orgId), body) =>
      val program: Future[Unit] =
        for {
          stamps              <- ctx.performIO(stampModel.get(body.stampIds))
          _                   <- assertAllExist(body.stampIds, stamps)
          _                   <- assertStampsNotClaimed(stamps)
          _                   <- assertAllBelongToOrg(stamps, orgId)
          cardTemplateVersion <- assertAllBelongToTheSameTemplate(stamps)
          cardTemplateOpt     <- getCardTemplate(orgId, cardTemplateVersion)
          cardTemplate        <- cardTemplateOpt.toFail(Fail.NotFound("card_template_not_found"))
          _                   <- assertCardNotExpired(stamps, cardTemplate)
          _                   <- assertCardFull(stamps, cardTemplate)
          _                   <- ctx.performIO(stampModel.claim(body.stampIds, clock.instant()))
        } yield ()
      program.toOut
    }

  def getCardTemplate(orgId: Id[Org], version: CardTemplateVersion): Future[Option[CardTemplate]] =
    ctx.performIO(cardTemplateModel.find(orgId, version))
}

object ClaimValidator {
  def assertAllExist(stampIds: Seq[Id[Stamp]], stamps: Seq[Stamp]): Future[Unit] = {
    val errIds = stampIds.toSet -- stamps.map(_.id)
    if (errIds.isEmpty) Future.successful(())
    else                Future.failed(Fail.IncorrectInputWithDetails("stamps_not_found", Map("stampIds" -> errIds).asJson))
  }

  def assertAllBelongToOrg(stamps: Seq[Stamp], orgId: Id[Org]): Future[Unit] = {
    val errIds = stamps.filter(_.orgId != orgId).map(_.id)
    if (errIds.isEmpty) Future.successful(())
    else                Future.failed(Fail.IncorrectInputWithDetails("stamps_diff_org", Map("stampIds" -> errIds).asJson))
  }

  def assertAllBelongToTheSameTemplate(stamps: Seq[Stamp]): Future[CardTemplateVersion] = {
    val versions = stamps.map(_.cardTemplateVersion).toSet
    if (versions.size == 1) Future.successful(versions.head)
    else Future.failed(Fail.IncorrectInput("stamps_diff_card_templates"))
  }

  def assertStampsNotClaimed(stamps: Seq[Stamp]): Future[Unit] = {
    val errIds = stamps.filter(_.claimedOn.isDefined).map(_.id)
    if (errIds.isEmpty) Future.successful(())
    else                Future.failed(Fail.IncorrectInputWithDetails("stamps_claimed", Map("stampIds" -> errIds).asJson))
  }

  def assertCardNotExpired(stamps: Seq[Stamp], template: CardTemplate)(implicit clock: Clock): Future[Unit] = { // TODO test it
    val cardCreatedOn = stamps.map(_.createdOn).min
    val cardExpired = {
      val createdOnCalendar = Calendar.getInstance()
      createdOnCalendar.setTimeInMillis(cardCreatedOn.toEpochMilli)
      createdOnCalendar.add(Calendar.MONTH, template.cardExpiresInMonths)
      val expiresAt = createdOnCalendar.toInstant

      clock.instant().isAfter(expiresAt)
    }

    if (!cardExpired) Future.successful(())
    else              Future.failed(Fail.IncorrectInput("card_expired"))
  }

  def assertCardFull(stamps: Seq[Stamp], template: CardTemplate): Future[Unit] =
    if (template.size <= stamps.size ) Future.successful(()) else Future.failed(Fail.IncorrectInput("card_not_full"))
}

object endpoints extends Http {
  val StampPath = "stamp"

  val register =
    secureEndpoint.post
      .in(StampPath / "register")
      .in(jsonBody[Stamp_Register_IN])
      .out(jsonBody[Stamp_Register_OUT])
      .tag("stamp")
      .description(
        s"""
           |Register stamp.
           |Requires 'Authorization: Bearer {apiKey}' header where 'apiKey' obtained from '${selectOrg.info.name.get}' route.
           |""".stripMargin)

  val claim =
    secureEndpoint.post
      .in(StampPath / "claim")
      .in(jsonBody[Stamp_Claim_IN])
      .tag("stamp")
      .description(
        s"""
           |Claim stamps.
           |Requires 'Authorization: Bearer {apiKey}' header where 'apiKey' obtained from '${selectOrg.info.name.get}' route.
           |""".stripMargin)
}

object models {
  case class Stamp_Register_IN(stampId: Id[Stamp])
  case class Stamp_Register_OUT(cardTemplateVersion: CardTemplateVersion, stampsAvailable: Int)
  case class Stamp_Claim_IN(stampIds: List[Id[Stamp]])
}
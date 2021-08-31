package com.stampy.service.modules.org

import java.time.Clock

import com.stampy.service.Fail
import com.stampy.service.db.Postgres
import com.stampy.service.db.Postgres.PostgresCtx
import com.stampy.service.domain.Permission.Owner
import com.stampy.service.domain._
import com.stampy.service.infrastructure.Codecs._
import com.stampy.service.modules.org.models._
import com.stampy.service.modules.packet.StampPacketModel
import com.stampy.service.modules.security.ApiKeyModel
import com.stampy.service.modules.user.UserModel
import com.stampy.service.tapir.{Auth, Error_OUT, Http, TapirRoute}
import com.stampy.service.util._
import sttp.model.StatusCode
import sttp.tapir.server.ServerEndpoint

import scala.concurrent.{ExecutionContext, Future}


case class RegisterOrgConfig(freeStamps: Int, freeStampsValidForDays: Int)
class RegisterRoute(val ctx: PostgresCtx, auth: Auth, config: RegisterOrgConfig)(implicit val ec: ExecutionContext, clock: Clock)
  extends TapirRoute with Postgres with OrgModel with MembershipModel with CardTemplateModel with StampPacketModel {

  def freePacket(id: Id[StampPacket], orgId: Id[Org]) =
    StampPacket(id, orgId, config.freeStamps, 0, clock.instant(), clock.instant().plusSeconds(config.freeStampsValidForDays * 24 * 3600))

  override def spec: ServerEndpoint[(Id[ApiKey], Org_Register_IN), (StatusCode, Error_OUT), Org_Register_OUT, Nothing, Future] =
    endpoints.register
      .serverLogicPart(auth.authn)
      .andThen { case (apiKey, body) =>
        val org       = Org(DefaultIdGenerator.nextId(), apiKey.userId, body.name, clock.instant())
        val template  = CardTemplate(org.id, CardTemplateVersion(0), body.cardTemplate.description, body.cardTemplate.size, body.cardTemplate.cardExpiresInMonths, clock.instant())
        val packet    = freePacket(DefaultIdGenerator.nextId(), org.id)

        val program: Future[Org] =
          ctx.performTransactIO(
            for {
              _ <- orgModel.create(org)
              _ <- orgMemberModel.assign(apiKey.userId, Membership(OwnerRole, org.id))
              _ <- cardTemplateModel.create(template)
              _ <- stampPacketModel.create(packet)
              _  = log.info(s"Registered org with card template and stamp packet: $org $template $packet")
            } yield org
          )

        program.map(org => Org_Register_OUT(org.id, config.freeStamps, config.freeStampsValidForDays)).toOut
      }
}

class OwnedOrgsRoute(val ctx: PostgresCtx, auth: Auth)(implicit val ec: ExecutionContext) extends TapirRoute with Postgres with OrgModel {
  override def spec: ServerEndpoint[Id[ApiKey], (StatusCode, Error_OUT), Org_Owned_OUT, Nothing, Future] =
    endpoints.listOwnedOrgs
      .serverLogicPart(auth.authn)
      .andThen { case (apiKey, ()) =>
        val program: Future[List[Org]] =
          ctx.performIO(orgModel.listByOwner(apiKey.userId))

        program.map(Org_Owned_OUT(_)).toOut
      }
}

class CreateOrgInviteRoute(val ctx: PostgresCtx, auth: Auth)(implicit val ec: ExecutionContext, clock: Clock) extends TapirRoute with Postgres with OrgInviteModel with UserModel with MembershipModel {
  override def spec: ServerEndpoint[(Id[ApiKey], Org_Invite_IN), (StatusCode, Error_OUT), Org_Invite_OUT, Nothing, Future] =
    endpoints.createOrgInvite
      .serverLogicPart(auth.authzWithOrg(Permission.Owner))
      .andThen { case ((_, orgId), body) =>
        val program: Future[OrgInvite] =
          for {
            userOpt   <- ctx.performIO(userModel.findByEmail(body.email.lowerCased))
            user      <- userOpt.toFail(Fail.NotFound("user_not_found"))
            inviteOpt <- ctx.performIO(orgInviteModel.getByUserIdAndOrgId(user.id, orgId))
            _         <- inviteOpt.toFailIfExists(Fail.Conflict("user_already_invited"))
            roleOpt   <- ctx.performIO(orgMemberModel.findRole(user.id, orgId))
            _         <- roleOpt.toFailIfExists(Fail.Conflict("user_already_member"))
            invite     = OrgInvite(user.id, orgId, clock.instant())
            _         <- ctx.performIO(orgInviteModel.create(invite))
            _          = log.info(s"Created org invite: $invite")
          } yield invite

        program.map(Org_Invite_OUT(_)).toOut
      }
}


class AcceptOrgInviteRoute(val ctx: PostgresCtx, auth: Auth)(implicit val ec: ExecutionContext) extends TapirRoute with Postgres with OrgInviteModel with MembershipModel {
  override def spec: ServerEndpoint[(Id[ApiKey], Id[Org]), (StatusCode, Error_OUT), Unit, Nothing, Future] =
    endpoints.acceptOrgInvite
      .serverLogicPart(auth.authn)
      .andThen { case (apiKey, orgId) =>
        val program: Future[Unit] =
          for {
            inviteOpt               <- ctx.performIO(orgInviteModel.getByUserIdAndOrgId(apiKey.userId, orgId))
            _                       <- inviteOpt.toFail(Fail.NotFound("invite_not_found"))
            _                       <- ctx.performTransactIO(acceptInviteQueries(apiKey, orgId))
            _                        = log.info(s"Accepted org invite: $inviteOpt")
          } yield ()

        program.toOut
      }

  private def acceptInviteQueries(apiKey: ApiKey, orgId: Id[Org]) =
    for {
      _ <- orgInviteModel.delete(orgId, apiKey.userId)
      _ <- orgMemberModel.assign(apiKey.userId, Membership(MemberRole, orgId))
    } yield ()
}

class RejectOrgInviteRoute(val ctx: PostgresCtx, auth: Auth)(implicit val ec: ExecutionContext) extends TapirRoute with Postgres with OrgInviteModel with MembershipModel {
  override def spec: ServerEndpoint[(Id[ApiKey], Id[Org]), (StatusCode, Error_OUT), Unit, Nothing, Future] =
    endpoints.rejectOrgInvite
      .serverLogicPart(auth.authn)
      .andThen { case (apiKey, orgId) =>
        val program: Future[Unit] =
          for {
            inviteOpt               <- ctx.performIO(orgInviteModel.getByUserIdAndOrgId(apiKey.userId, orgId))
            _                       <- inviteOpt.toFail(Fail.NotFound("invite_not_found"))
            _                       <- ctx.performIO(orgInviteModel.delete(orgId, apiKey.userId))
            _                        = log.info(s"Rejected org invite: $inviteOpt")
          } yield ()

        program.toOut
      }
}

class RemoveMemberRoute(val ctx: PostgresCtx, auth: Auth)(implicit val ec: ExecutionContext) extends TapirRoute with Postgres with MembershipModel {
  override def spec: ServerEndpoint[(Id[ApiKey], Id[User]), (StatusCode, Error_OUT), Unit, Nothing, Future] =
    endpoints.removeMember
      .serverLogicPart(auth.authzWithOrg(Owner))
      .andThen { case ((userId, orgId), memberId) =>
        val program: Future[Unit] =
          for {
            _ <- if (userId == memberId) Future.failed(Fail.Conflict("member_is_owner")) else Future.successful(())
            _ <- ctx.performIO(orgMemberModel.remove(memberId, orgId))
            _  = log.info(s"Removed member '$memberId' from org '$orgId'")
          } yield ()

        program.toOut
      }
}

class SelectOrgRoute(val ctx: PostgresCtx, auth: Auth)(implicit val ec: ExecutionContext, clock: Clock) extends TapirRoute with Postgres with MembershipModel with ApiKeyModel {
  override def spec =
    endpoints.selectOrg
      .serverLogicPart(auth.authn)
      .andThen { case (apiKey, orgId) =>
        val program: Future[Id[ApiKey]] =
          for {
            roleOpt <- ctx.performIO(orgMemberModel.findRole(apiKey.userId, orgId))
            role <- roleOpt.toFail(Fail.IncorrectInput("not_a_member"))
            orgApiKey <- ctx.performTransactIO(
              apiKeyModel.delete(apiKey.id)
                .flatMap(_ => apiKeyModel.create(apiKey.userId, Some(Membership(role, orgId)), clock.instant(), clock.instant().plusSeconds(3600 * 24 * 10)))
            )
          } yield orgApiKey.id

        program.map(Org_Select_OUT(_)).toOut
      }
}

class GetOrgStatusRoute(val ctx: PostgresCtx, auth: Auth)(implicit val ec: ExecutionContext, clock: Clock) extends TapirRoute with Postgres with MembershipModel with OrgInviteModel with StampPacketModel {
  override def spec =
    endpoints.getOrgStatus
      .serverLogicPart(auth.authzWithOrg(Owner))
      .andThen { case ((_, orgId), _) =>
        val program: Future[(List[OrgInviteWithInviteeEmail], List[MemberWithUserEmail], List[StampPacket])] =
          for {
            invites <- ctx.performIO(orgInviteModel.listByOrgIdWithInviteeEmail(orgId))
            members <- ctx.performIO(orgMemberModel.listByOrgIdWithMemberEmail(orgId))
            packets <- ctx.performIO(stampPacketModel.getAllActive(orgId, clock.instant()))
          } yield (invites, members, packets)

        program.map((Get_Org_Status_OUT.apply _).tupled).toOut
      }
}

object endpoints extends Http {
  val OrgPath = "org"

  val register =
    secureEndpoint.post
      .in(OrgPath / "register")
      .in(jsonBody[Org_Register_IN])
      .out(jsonBody[Org_Register_OUT])
      .tag("org")
      .description(
        s"""
           |Register organization.
           |""".stripMargin)

  val listOwnedOrgs =
    secureEndpoint.get
      .in(OrgPath / "owned")
      .out(jsonBody[Org_Owned_OUT])
      .tag("org")
      .description(
        s"""
           |Get owned organizations.
           |""".stripMargin)

  val createOrgInvite =
    secureEndpoint.post
      .in(OrgPath / "invite")
      .in(jsonBody[Org_Invite_IN])
      .out(jsonBody[Org_Invite_OUT])
      .tag("org")
      .description(
        s"""
           |Invite user to organization.
           |Requires 'Authorization: Bearer {apiKey}' header where 'apiKey' obtained from '${selectOrg.info.name.get}' route.
           |""".stripMargin)

  val acceptOrgInvite =
    secureEndpoint.get
      .in(OrgPath / path[Id[Org]]("orgId") / "invite" / "accept")
      .tag("org")
      .description(
        s"""
           |Accept organization invite.
           |You need to call '${selectOrg.info.name.get}' route to retrieve 'apiKey' with organization context.
           |""".stripMargin)

  val rejectOrgInvite =
    secureEndpoint.get
      .in(OrgPath / path[Id[Org]]("orgId") / "invite" / "reject")
      .tag("org")
      .description(
        s"""
           |Reject organization invite.
           |You need to call '${selectOrg.info.name.get}' route to retrieve 'apiKey' with organization context.
           |""".stripMargin)

  val removeMember =
    secureEndpoint.get
      .in(OrgPath / "member" / path[Id[User]]("memberId") / "remove")
      .tag("org")
      .description(
        s"""
           |Remove member from organization.
           |You need to call '${selectOrg.info.name.get}' route to retrieve 'apiKey' with organization context.
           |""".stripMargin)

  lazy val selectOrg =
    secureEndpoint.get
      .in(OrgPath / path[Id[Org]]("orgId") / "select")
      .out(jsonBody[Org_Select_OUT])
      .tag("org")
      .name("select-organization")
      .description(
        s"""
           |Select organization. Returns 'apiKey' with organization context. Use it in 'Authorization: Bearer {apiKey}' header.
           |""".stripMargin)

  lazy val getOrgStatus =
    secureEndpoint.get
      .in(OrgPath / "status")
      .out(jsonBody[Get_Org_Status_OUT])
      .tag("org")
      .description(
        s"""
           |Get organization invites and members. Owner only.
           |You need to call '${selectOrg.info.name.get}' route to retrieve 'apiKey' with organization context.
           |""".stripMargin)
}

object models {
  case class Org_Register_IN(name: String, cardTemplate: Card_Template_IN)
  case class Card_Template_IN(description: String, size: Int, cardExpiresInMonths: Int)
  case class Org_Register_OUT(orgId: Id[Org], freeStamps: Int, stampsValidForDays: Int)

  case class Org_Owned_OUT(orgs: List[Org])

  case class Org_Select_OUT(apiKey: Id[ApiKey])

  case class Get_Org_Status_OUT(invites: List[OrgInviteWithInviteeEmail], members: List[MemberWithUserEmail], packets: List[StampPacket])

  case class Org_Invite_IN(email: String)
  case class Org_Invite_OUT(invite: OrgInvite)
}
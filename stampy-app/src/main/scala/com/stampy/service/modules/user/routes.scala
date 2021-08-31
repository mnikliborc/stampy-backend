package com.stampy.service.modules.user

import java.time.{Clock, Instant}

import com.stampy.service.Fail
import com.stampy.service.db.Postgres
import com.stampy.service.db.Postgres.PostgresCtx
import com.stampy.service.domain._
import com.stampy.service.infrastructure.Codecs._
import com.stampy.service.modules.org.{MembershipModel, OrgInviteModel}
import com.stampy.service.modules.security.ApiKeyModel
import com.stampy.service.modules.user.models._
import com.stampy.service.tapir.{Auth, Error_OUT, Http, TapirRoute}
import com.stampy.service.util._
import sttp.model.StatusCode
import sttp.tapir.server.ServerEndpoint

import scala.concurrent.{ExecutionContext, Future}

class RegisterRoute(val ctx: PostgresCtx)(implicit val ec: ExecutionContext, clock: Clock) extends TapirRoute with Postgres with UserModel with ApiKeyModel { self =>
  override def spec: ServerEndpoint[User_Register_IN, (StatusCode, Error_OUT), User_Register_OUT, Nothing, Future] =
    endpoints.register.serverLogic { in =>
      val user = User(DefaultIdGenerator.nextId(), in.email.lowerCased, clock.instant())
      val program: Future[ApiKey] =
        ctx.performTransactIO {
          for {
            _      <- userModel.create(user)
            apiKey <- apiKeyModel.create(user.id, None, clock.instant(), clock.instant().plusSeconds(3600 * 24 * 10))
            _        = log.info(s"Registered user: $user")
          } yield apiKey
        }

      program.map(apiKey => User_Register_OUT(apiKey.id)).toOut
    }
}

class LoginRoute(val ctx: PostgresCtx)(implicit val ec: ExecutionContext, clock: Clock) extends TapirRoute with ApiKeyModel with UserModel {
  override val spec: ServerEndpoint[User_Login_IN, (StatusCode, Error_OUT), User_Login_OUT, Nothing, Future] =
    endpoints.login.serverLogic { case (body) =>
      val program: Future[ApiKey] =
        for {
          userOpt <- ctx.performIO(userModel.findByEmail(body.email.lowerCased))
          user    <- userOpt.toFail(Fail.Unauthorized)
          apiKey  <- ctx.performIO(apiKeyModel.create(user.id, None, clock.instant(), clock.instant().plusSeconds(3600 * 24 * 10)))
          _        = log.info(s"Logged in: $user")
        } yield apiKey

      program.map(apiKey => User_Login_OUT(apiKey.id)).toOut
    }
}

class GetSelfRoute(val ctx: PostgresCtx, auth: Auth)(implicit val ec: ExecutionContext) extends TapirRoute with ApiKeyModel with UserModel {
  override val spec: ServerEndpoint[Id[ApiKey], (StatusCode, Error_OUT), User_GetSelf_OUT, Nothing, Future] =
    endpoints.getSelf
      .serverLogicPart(auth.authn)
      .andThen { case (apiKey, ()) =>
        val program: Future[User] =
          ctx.performIO(userModel.findById(apiKey.userId))
            .flatMap {
              case Some(user) => Future.successful(user)
              case None => Future.failed(Fail.NotFound("user_not_found"))
            }

        program.map(User_GetSelf_OUT).toOut
      }
}

class ListOrgInvitesRoute(val ctx: PostgresCtx, auth: Auth)(implicit val ec: ExecutionContext) extends TapirRoute with Postgres with OrgInviteModel with UserModel {
  override def spec: ServerEndpoint[Id[ApiKey], (StatusCode, Error_OUT), User_ListOrgInvites_OUT, Nothing, Future] =
    endpoints.listOrgInvites
      .serverLogicPart(auth.authn)
      .andThen { case (apiKey, ()) =>
        val program: Future[List[OrgInviteWithName]] =
          for {
            invites <- ctx.performIO(orgInviteModel.listByUserIdWithOrgName(apiKey.userId))
          } yield invites

        program.map(User_ListOrgInvites_OUT).toOut
      }
}

class ListOrgMembershipRoute(val ctx: PostgresCtx, auth: Auth)(implicit val ec: ExecutionContext) extends TapirRoute with Postgres with MembershipModel {
  override def spec: ServerEndpoint[Id[ApiKey], (StatusCode, Error_OUT), User_ListOrgMembership_OUT, Nothing, Future] =
    endpoints.listOrgMemberships
      .serverLogicPart(auth.authn)
      .andThen { case (apiKey, ()) =>
        val program: Future[List[MembershipWithOrgName]] =
          for {
            memberships <- ctx.performIO(orgMemberModel.listByUserIdWithOrgName(apiKey.userId))
          } yield memberships

        program.map(User_ListOrgMembership_OUT).toOut
      }
}

object endpoints extends Http {
  import com.stampy.service.modules.org.endpoints.selectOrg

  val register =
    baseEndpoint.post
      .in(UserPath / "register")
      .in(jsonBody[User_Register_IN])
      .out(jsonBody[User_Register_OUT])
      .tag("anonymous")
      .description(
        s"""
          |Register user. Returns 'apiKey' to be used as 'Authorization: bearer' header.
          |You need to call '${selectOrg.info.name.get}' route to retrieve 'apiKey' with organization context.
          |""".stripMargin)

  val login =
    baseEndpoint.post
      .in(UserPath / "login")
      .in(jsonBody[User_Login_IN])
      .out(jsonBody[User_Login_OUT])
      .tag("anonymous")
      .description(
        s"""
          |Login user. Returns 'apiKey' to be used as 'Authorization: bearer' header.
          |You need to call '${selectOrg.info.name.get}' route to retrieve 'apiKey' with organization context.
          |""".stripMargin)

  val getSelf =
    secureEndpoint.get
      .in(UserPath / "self")
      .out(jsonBody[User_GetSelf_OUT])
      .tag("user")
      .description(
        """
          |Get user.
          |""".stripMargin)

  val listOrgInvites =
    secureEndpoint.get
      .in(UserPath / "invites")
      .out(jsonBody[User_ListOrgInvites_OUT])
      .tag("user")
      .description(
        """
          |List organization invites.
          |""".stripMargin)

  val listOrgMemberships =
    secureEndpoint.get
      .in(UserPath / "memberships")
      .out(jsonBody[User_ListOrgMembership_OUT])
      .tag("user")
      .description(
        """
          |List organization memberships.
          |""".stripMargin)
}

object models {
  val UserPath = "user"

  case class User_Register_IN(email: String)
  case class User_Register_OUT(apiKey: Id[ApiKey])

  case class User_Login_IN(email: String, apiKeyValidHours: Option[Int])
  case class User_Login_OUT(apiKey: Id[ApiKey])

  case class User_GetSelf_OUT(user: User)

  case class User_ListOrgInvites_OUT(invites: List[OrgInviteWithName])

  case class User_ListOrgMembership_OUT(memberships: List[MembershipWithOrgName])
}
package com.stampy.service.tapir

import com.stampy.service.domain._
import com.stampy.service.modules.security.ApiKeyService
import com.stampy.service.util.Id
import io.circe.Json
import sttp.model.StatusCode
import sttp.tapir.Endpoint

import scala.concurrent.{ExecutionContext, Future}

class Auth(apiKeyService: ApiKeyService)(implicit ec: ExecutionContext) {

  def authn(token: Id[ApiKey]): Future[Either[(StatusCode, Error_OUT), ApiKey]] =
    apiKeyService.auth(token).map {
      case Some(apiKey) => Right(apiKey)
      case None => Left((StatusCode.Unauthorized, Error_OUT("token_expired", None)))
    }

  def authnWithOrg(token: Id[ApiKey]): Future[Either[(StatusCode, Error_OUT), (Id[User], Id[Org])]] =
    authn(token).map(_.flatMap(assertWithOrg))

  private def assertWithOrg(apiKey: ApiKey): Either[(StatusCode, Error_OUT), (Id[User], Id[Org])] =
    apiKey.membership match {
      case Some(membership) => Right(apiKey.userId, membership.orgId)
      case None             => Left((StatusCode.Unauthorized, Error_OUT(s"org_not_selected", None)))
    }

  def authz(permissions: Permission*)(token: Id[ApiKey]): Future[Either[(StatusCode, Error_OUT), ApiKey]] =
    apiKeyService.auth(token).map {
      case Some(apiKey) =>
        val missingPermissions = permissions.toSet -- apiKey.membership.map(_.role.permissions).getOrElse(Set())
        if (missingPermissions.isEmpty) Right(apiKey)
        else Left((StatusCode.Forbidden, Error_OUT(s"missing_permissions", Some(Json.fromValues(missingPermissions.map(_.toString).map(Json.fromString))))))
      case None => Left((StatusCode.Unauthorized, Error_OUT("token_expired", None)))
    }

  def authzWithOrg(permissions: Permission*)(token: Id[ApiKey]): Future[Either[(StatusCode, Error_OUT), (Id[User], Id[Org])]] =
    authz(permissions:_*)(token).map(_.flatMap(assertWithOrg))

  implicit class AuthEndpointOps(e: Endpoint[Id[ApiKey], (StatusCode, Error_OUT), _, _]) {
    def requireAuthn = e.serverLogicPart(authn)
    def requireAuthnWithOrg = e.serverLogicPart(authnWithOrg)

    def requireAuthz(permissions: Permission*) = e.serverLogicPart(authz(permissions:_*))
    def requireAuthzWithOrg(permissions: Permission*) = e.serverLogicPart(authzWithOrg(permissions:_*))
  }
}

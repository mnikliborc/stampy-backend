package com.stampy.service.modules.security

import java.time.Instant

import com.stampy.service.db.Postgres
import com.stampy.service.domain.{ApiKey, Org, Membership, Role, User}
import com.stampy.service.util.{DefaultIdGenerator, Id}

trait ApiKeyModel extends Postgres {
  object apiKeyModel {

    import ctx._

     case class ApiKeyRow(
      id: Id[ApiKey],
      userId: Id[User],
      role: Option[Role],
      orgId: Option[Id[Org]],
      createdOn: Instant,
      validUntil: Instant
    )

    val apiKeys = quote(querySchema[ApiKeyRow]("api_keys"))

    def create(userId: Id[User], membership: Option[Membership], created: Instant, validUntil: Instant) = {
      val key = ApiKey(DefaultIdGenerator.nextId[ApiKey](), userId, membership, created, validUntil)
      ctx.runIO(apiKeys.insert(lift(toApiKeyRow(key)))).map(_ => key)
    }

    def get(token: Id[ApiKey]) =
      ctx.runIO(apiKeys.filter(_.id == lift(token))).map(_.headOption.map(toApiKey))

    def delete(apiKeyId: Id[ApiKey]) =
      ctx.runIO(apiKeys.filter(_.id == lift(apiKeyId)).delete)

    private def toApiKeyRow(key: ApiKey): ApiKeyRow =
      ApiKeyRow(
        id = key.id,
        userId = key.userId,
        role = key.membership.map(_.role),
        orgId = key.membership.map(_.orgId),
        createdOn = key.createdOn,
        validUntil = key.validUntil
      )

    private def toApiKey(row: ApiKeyRow): ApiKey =
      ApiKey(
        id = row.id,
        userId = row.userId,
        membership = {
          for {
            role <- row.role
            orgId <- row.orgId
          } yield Membership(role, orgId)
        },
        createdOn = row.createdOn,
        validUntil = row.validUntil
      )
  }
}
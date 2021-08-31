package com.stampy.service.modules.security

import java.time.{Clock, Instant}

import com.stampy.service.db.Postgres
import com.stampy.service.db.Postgres.PostgresCtx
import com.stampy.service.domain.ApiKey
import com.stampy.service.util._

import scala.concurrent.{ExecutionContext, Future}

trait ApiKeyService {
  def auth(token: Id[ApiKey]): Future[Option[ApiKey]]
}

class ApiKeyServiceImpl(val ctx: PostgresCtx)(implicit val ec: ExecutionContext, clock: Clock) extends ApiKeyService with Logger with Postgres with ApiKeyModel {
  override def auth(token: Id[ApiKey]): Future[Option[ApiKey]] =
    ctx.performIO[Option[ApiKey]](apiKeyModel.get(token).map(_.flatMap(verifyValid)))

  // TODO run periodic job to delete expired tokens
  private def verifyValid(apiKey: ApiKey): Option[ApiKey] =
    if (clock.instant().isAfter(apiKey.validUntil)) {
      log.info(s"api key expired: ${apiKey.id}")
      None
    } else {
      Some(apiKey)
    }
}
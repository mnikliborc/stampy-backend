package com.stampy.service.db

import java.time.Instant

import com.stampy.service.domain.{CardTemplateVersion, MemberRole, OwnerRole, Role}
import com.stampy.service.util.{Id, LowerCased}
import com.typesafe.config.Config
import io.getquill.{MappedEncoding, PostgresJAsyncContext, SnakeCase}
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.MigrationVersion
import org.flywaydb.core.api.configuration.ClassicConfiguration
import org.joda.time.DateTime

import scala.concurrent.ExecutionContext
import scala.util.Try

trait Postgres extends PostgresMappings {
  implicit def ec: ExecutionContext
  val ctx: PostgresJAsyncContext[SnakeCase.type]

  implicit class CtxOps(_ctx: PostgresJAsyncContext[SnakeCase.type]) {
    def performTransactIO[T](io: ctx.IO[T, _])(implicit ec: ExecutionContext) = ctx.performIO(io, true)
  }

  implicit class InstantQuotes(left: Instant) {
    import ctx._
    def >(right: Instant) = quote(infix"$left > $right".as[Boolean])

    def <(right: Instant) = quote(infix"$left < $right".as[Boolean])
  }
}

trait PostgresMappings {
  implicit def IdEncoding[A] = MappedEncoding[Id[A], String](o => o.value)
  implicit def IdDecoding[A] = MappedEncoding[String, Id[A]](Id[A](_))

  implicit val LowerCasedEncoding = MappedEncoding[LowerCased, String](o => o.value)
  implicit val LowerCasedDecoding = MappedEncoding[String, LowerCased] { s => LowerCased(s) }

  implicit val encodeDate = MappedEncoding[DateTime, Instant](dt => Instant.ofEpochMilli(dt.toInstant.getMillis))
  implicit val decodeDate = MappedEncoding[Instant, DateTime](i => new DateTime(i.toEpochMilli))

  implicit val RoleEncoding = MappedEncoding[Role, String] {
    case OwnerRole  => "owner"
    case MemberRole => "worker"
  }
  implicit val RoleDecoding = MappedEncoding[String, Role] {
    case "owner"  => OwnerRole
    case "worker" => MemberRole
  }

}

object Postgres {
  type PostgresCtx = PostgresJAsyncContext[SnakeCase.type]

  def ctx(postgresConfig: Config): Try[PostgresCtx] = Try {
    new PostgresJAsyncContext(SnakeCase, postgresConfig)
  }

  def migrate(postgresConfig: Config): Try[Unit] = Try {
    val url = postgresConfig.getString("url")
    val username = postgresConfig.getString("username")
    val password = postgresConfig.getString("password")

    FlywayMigration.migrate(url, username, password)
  }
}

object FlywayMigration {
  def migrate(url: String, user: String, password: String): Unit = {
    val config = new ClassicConfiguration
    config.setBaselineOnMigrate(true)
    config.setLocationsAsStrings("migrations/postgres")
    config.setDataSource(url, user, password)
    config.setBaselineVersion(MigrationVersion.fromVersion("0"))

    Flyway.configure().configuration(config).load.migrate()
  }
}

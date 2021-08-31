package com.stampy.service.tapir

import cats.implicits._
import com.stampy.service.Fail
import com.stampy.service.domain.{ApiKey, CardTemplateVersion, Role}
import com.stampy.service.infrastructure.Codecs._
import com.stampy.service.util.{Id, Logger}
import io.circe.Json
import sttp.model.StatusCode
import sttp.tapir.Codec.PlainCodec
import sttp.tapir._
import sttp.tapir.json.circe.TapirJsonCirce
import tsec.common.SecureRandomId

import scala.concurrent.{ExecutionContext, Future}

object Http extends Http
trait Http extends Tapir with TapirJsonCirce with TapirSchemas with Logger {
  /**
   * Description of the output, that is used to represent an error that occurred during endpoint invocation.
   */
  val failOutput: EndpointOutput[(StatusCode, Error_OUT)] = statusCode and jsonBody[Error_OUT]

  /**
   * Base endpoint description for non-secured endpoints. Specifies that errors are always returned as JSON values
   * corresponding to the [[Error_OUT]] class.
   */
  val baseEndpoint: Endpoint[Unit, (StatusCode, Error_OUT), Unit, Nothing] =
    endpoint.errorOut(failOutput)

  /**
   * Base endpoint description for secured endpoints. Specifies that errors are always returned as JSON values
   * corresponding to the [[Error_OUT]] class, and that authentication is read from the `Authorization: Bearer` header.
   */
  val secureEndpoint: Endpoint[Id[ApiKey], (StatusCode, Error_OUT), Unit, Nothing] =
    baseEndpoint.in(auth.bearer[String].map(Id[ApiKey](_))(_.value))

  private val InternalServerError = (StatusCode.InternalServerError, "Internal server error", None)
  private val failToResponseData: Fail => (StatusCode, String, Option[Json]) = {
    case Fail.NotFound(what)      => (StatusCode.NotFound, what, None)
    case Fail.Conflict(msg)       => (StatusCode.Conflict, msg, None)
    case Fail.IncorrectInput(msg) => (StatusCode.BadRequest, msg, None)
    case Fail.IncorrectInputWithDetails(msg, details) => (StatusCode.BadRequest, msg, Some(details))
    case Fail.Forbidden           => (StatusCode.Forbidden, "Forbidden", None)
    case Fail.Unauthorized        => (StatusCode.Unauthorized, "Unauthorized", None)
    case _                        => InternalServerError
  }

  def exceptionToErrorOut(e: Exception): (StatusCode, Error_OUT) = {
    val (statusCode, message, details) = e match {
      case f: Fail => failToResponseData(f)
      case _ =>
        log.error("Exception when processing request", e)
        InternalServerError
    }

    val errorOut = Error_OUT(message, details)
    (statusCode, errorOut)
  }

  implicit class FutureOut[T](f: Future[T])(implicit ec: ExecutionContext) {
    def toOut: Future[Either[(StatusCode, Error_OUT), T]] = {
      f.map(t => t.asRight[(StatusCode, Error_OUT)]).recover {
        case e: Exception => exceptionToErrorOut(e).asLeft[T]
      }
    }
  }

  implicit class OptionOps[T](o: Option[T]) {
    def toFail(f: Fail): Future[T] =
      o.fold[Future[T]](Future.failed(f))(Future.successful)

    def toFailIfExists(f: Fail): Future[Unit] =
      o.fold[Future[Unit]](Future.successful(()))(_ => Future.failed(f))
  }

  implicit def IdPathTapirCodec[A]: Codec[String, Id[A], CodecFormat.TextPlain] = Codec.string.map(Id[A](_))(_.value)
}

trait TapirSchemas extends TapirJsonCirce {
  implicit val idPlainCodec: PlainCodec[SecureRandomId] =
    Codec.string.map(_.asInstanceOf[SecureRandomId])(identity)

  implicit val schemaForBigDecimal: Schema[BigDecimal] = Schema(SchemaType.SString)
  implicit def schemaForId[A]: Schema[Id[A]] = Schema(SchemaType.SString)
  implicit def schemaForCardTemplateVersion[A]: Schema[CardTemplateVersion] = Schema(SchemaType.SInteger)
  implicit def schemaForRole: Schema[Role] = Schema(SchemaType.SString)
}

case class Error_OUT(error: String, details: Option[Json])
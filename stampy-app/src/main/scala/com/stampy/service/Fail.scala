package com.stampy.service

import io.circe.Json

/**
 * Base class for all failures in the application. The failures are translated to HTTP API results in the
 * [[com.stampy.service.tapir.Http]] class.
 *
 * The class hierarchy is not sealed and can be extended as required by specific functionalities.
 */
abstract class Fail extends Exception

object Fail {
  case class NotFound(what: String) extends Fail
  case class Conflict(msg: String) extends Fail
  case class IncorrectInput(msg: String) extends Fail
  case class IncorrectInputWithDetails(msg: String, details: Json) extends Fail
  case object Unauthorized extends Fail
  case object Forbidden extends Fail
  case object ServerError extends Fail
}

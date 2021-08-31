package com.stampy.service.tapir

import java.nio.file.{Files, Paths}

import com.stampy.service.Fail
import com.stampy.service.infrastructure.AccessLogger
import io.vertx.scala.core.Vertx
import io.vertx.scala.ext.web.Router
import io.vertx.scala.ext.web.handler.StaticHandler
import sttp.model.StatusCode
import sttp.tapir.DecodeResult
import sttp.tapir.server._
import sttp.tapir.server.vertx.{VertxEndpointOptions, _}

import scala.concurrent.Future
import scala.util.Try

object TapirRouter extends Http {
  /**
   * tapir's Codecs parse inputs - query parameters, JSON bodies, headers - to their desired types. This might fail,
   * and then a decode failure is returned, instead of a value. How such a failure is handled can be customised.
   *
   * We want to return responses in the same JSON format (corresponding to [[Error_OUT]]) as other errors returned
   * during normal request processing.
   *
   * We use the default behavior of tapir (`ServerDefaults.decodeFailureHandler`), customising the format
   * used for returning errors (`http.failOutput`). This will cause `400 Bad Request` to be returned in most cases.
   *
   * Additionally, if the error thrown is a `Fail` we might get additional information, such as a custom status
   * code, by translating it using the `http.exceptionToErrorOut` method and using that to create the response.
   */
  private val decodeFailureHandler: DecodeFailureHandler = {
    def failResponse(code: StatusCode, msg: String): DecodeFailureHandling =
      DecodeFailureHandling.response(failOutput)((code, Error_OUT(msg, None)))

    val defaultHandler = ServerDefaults.decodeFailureHandler.copy(response = failResponse)

    {
      // if an exception is thrown when decoding an input, and the exception is a Fail, responding basing on the Fail
      case DecodeFailureContext(_, DecodeResult.Error(_, f: Fail)) =>
        DecodeFailureHandling.response(failOutput)(exceptionToErrorOut(f))
      // otherwise, converting the decode input failure into a response using tapir's defaults
      case ctx =>
        defaultHandler(ctx)
    }
  }

  implicit val options: VertxEndpointOptions = VertxEndpointOptions(decodeFailureHandler = decodeFailureHandler)

  def apply(vertx: Vertx, routes: List[TapirRoute]): Router = {
    val router = Router.router(vertx)

    router.route().handler(AccessLogger.create())

    routes.map(_.spec.asInstanceOf[ServerEndpoint[_, _, _, Any, Future]])
      .map(VertxServerEndpoint(_).route)
      .foreach(_.apply(router))

    saveOpenApi(routes)
    router.route("/docs/*").handler(StaticHandler.create("docs").setCachingEnabled(false).handle)

    router
  }

  def saveOpenApi(routes: List[TapirRoute]): Unit = {
    import sttp.tapir.docs.openapi._
    import sttp.tapir.openapi.circe.yaml._

    val openapi = routes.map(_.spec.endpoint).groupBy(_.info.tags.headOption).toList.sortBy(_._1).map(_._2).flatten // endpoints grouped and sorted by tags
      .toOpenAPI("stampy", "1.0.0")

    Try(Files.createDirectory(Paths.get("docs")))
    Files.write(Paths.get("docs/openapi.yaml"), openapi.toYaml.getBytes)
  }
}
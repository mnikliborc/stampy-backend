package com.stampy.service.modules

import com.typesafe.config.Config
import sttp.client.{HttpURLConnectionBackend, Identity, NothingT, SttpBackend}
import sttp.model.Uri
import sttp.tapir.Endpoint

abstract class E2E extends BaseE2E with Db with Preconditions with Routes {
  implicit class ClientOps[I, E, O](e: Endpoint[I, E, O, Nothing]) {
    import sttp.tapir.client.sttp._
    import SttpClientOptions.default
    implicit val backend: SttpBackend[Identity, Nothing, NothingT] = HttpURLConnectionBackend()

    def send(i: I) = e.toSttpRequestUnsafe(Uri("localhost", 8080)).apply(i).send()
  }

  override def postgresConfig: Config = appConfig.postgres
}

package com.stampy.service.modules;

import com.opentable.db.postgres.embedded.EmbeddedPostgres;
import com.stampy.service.StampyApp;
import com.stampy.service.config.StampyAppConfig;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValueFactory;
import io.vertx.scala.core.Vertx;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import scala.concurrent.Await;
import scala.concurrent.duration.FiniteDuration;

import java.time.Clock;
import java.util.concurrent.TimeUnit;

@Ignore
abstract public class BaseE2E {
  private EmbeddedPostgres postgres;
  private Vertx vertx;
  StampyAppConfig appConfig;

  @Before
  public void setUp() throws Exception {
    postgres = EmbeddedPostgres.builder().setPort(5432).start();

    Config config = ConfigFactory.load().withValue("postgres.url", ConfigValueFactory.fromAnyRef("jdbc:postgresql://localhost:5432/postgres"));
    appConfig = overrideConfig(StampyAppConfig.read(config).get());
    vertx = Await.result(StampyApp.start(appConfig, clock()), FiniteDuration.apply(3, TimeUnit.SECONDS));
  }

  public StampyAppConfig overrideConfig(StampyAppConfig config) {
    return config;
  }

  public Clock clock() {
    return Clock.systemUTC();
  }

  @After
  public void tearDown() throws Exception {
    postgres.close();
    vertx.close();
  }
}

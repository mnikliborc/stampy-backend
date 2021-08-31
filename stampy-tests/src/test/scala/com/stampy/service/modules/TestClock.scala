package com.stampy.service.modules

import java.time.{Clock, Instant, ZoneId}

class TestClock extends Clock {
  override def getZone: ZoneId = ZoneId.of("UTC")
  override def withZone(zone: ZoneId): Clock = this

  var _instant: Instant = Instant.now()

  def set(instant: Instant) = _instant = instant

  override def instant(): Instant = _instant
}

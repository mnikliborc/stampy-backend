package com.stampy.service.modules.packet

import java.time.Instant

import com.stampy.service.db.Postgres
import com.stampy.service.domain.{Org, StampPacket}
import com.stampy.service.util.Id

trait StampPacketModel extends Postgres {
  object stampPacketModel {
    import ctx._

    val packets = quote(querySchema[StampPacket]("stamp_packets"))

    def create(packet: StampPacket) =
      ctx.runIO(packets.insert(lift(packet))) // TODO handle duplicate error

    def getAllActive(orgId: Id[Org], now: Instant) =
      ctx.runIO(packets.filter(p => p.orgId == lift(orgId) && p.validUntil > lift(now) && p.size > p.used)).map(_.toList)

    def getAll(orgId: Id[Org]) =
      ctx.runIO(packets.filter(p => p.orgId == lift(orgId))).map(_.toList)

    def incrementUsedStamps(id: Id[StampPacket]) =
      ctx.runIO(packets.filter(_.id == lift(id)).update(p => p.used -> (p.used + 1)))

    def delete(id: Id[StampPacket]) =
      ctx.runIO(packets.filter(_.id == lift(id)).delete)
  }
}

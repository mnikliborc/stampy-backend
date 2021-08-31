package com.stampy.service.modules.stamp

import java.time.Instant

import com.stampy.service.db.Postgres
import com.stampy.service.domain.Stamp
import com.stampy.service.modules.org.CardTemplateModel
import com.stampy.service.util.Id

trait StampModel extends Postgres with CardTemplateModel {
  object stampModel {
    import ctx._

    val stamps = quote(querySchema[Stamp]("stamps"))

    def create(stamp: Stamp) =
      ctx.runIO(stamps.insert(lift(stamp))) // TODO handle duplicate error

    def get(stampIds: Seq[Id[Stamp]]) =
      ctx.runIO(stamps.filter(s => lift(stampIds).contains(s.id))).map(_.toSeq)

    def claim(stampIds: Seq[Id[Stamp]], claimedOn: Instant) =
      ctx.runIO {
        liftQuery(stampIds).foreach { stampId =>
          stamps.filter(_.id == stampId).update(_.claimedOn -> lift(Option(claimedOn)))
        }
      }
  }
}

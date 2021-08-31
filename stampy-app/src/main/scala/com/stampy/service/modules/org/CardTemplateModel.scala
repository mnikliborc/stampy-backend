package com.stampy.service.modules.org

import com.stampy.service.db.Postgres
import com.stampy.service.domain.{CardTemplate, CardTemplateVersion, Org}
import com.stampy.service.util.Id

trait CardTemplateModel extends Postgres {
  object cardTemplateModel {
    import ctx._

    val templates = quote(querySchema[CardTemplate]("card_templates"))

    def create(template: CardTemplate) =
      ctx.runIO(templates.insert(lift(template))).map(_ => template)

    def findLatest(orgId: Id[Org]) =
      ctx.runIO(templates.filter(_.orgId == lift(orgId))).map(_.reverse.headOption)

    def find(orgId: Id[Org], version: CardTemplateVersion) =
      ctx.runIO(templates.filter(t => t.orgId == lift(orgId) && t.version == lift(version))).map(_.headOption)

  }
}

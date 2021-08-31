package com.stampy.service.modules.org

import com.stampy.service.db.Postgres
import com.stampy.service.domain.{Org, User}
import com.stampy.service.util.Id

trait OrgModel extends Postgres {
  object orgModel {
    import ctx._

    val orgs = quote(querySchema[Org]("orgs"))

    def create(org: Org) =
      ctx.runIO(orgs.insert(lift(org))).map(_ => org) // TODO handle duplicate error

    def listByOwner(ownerId: Id[User]) =
      ctx.runIO(orgs.filter(_.ownerId == lift(ownerId))).map(_.toList)

    def find(orgId: Id[Org]) =
      ctx.runIO(orgs.filter(_.id == lift(orgId))).map(_.headOption)
  }
}
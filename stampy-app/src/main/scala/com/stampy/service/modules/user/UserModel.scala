package com.stampy.service.modules.user

import com.stampy.service.domain.User
import com.stampy.service.db.Postgres
import com.stampy.service.util.{Id, LowerCased}

trait UserModel extends Postgres {
  object userModel {
    import ctx._

    val users = quote(querySchema[User]("users"))

    def create(user: User) =
      ctx.runIO(users.insert(lift(user))).map(_ => user) // TODO handle duplicate error

    def findByEmail(email: LowerCased) =
      ctx.runIO(users.filter(_.email == lift(email))).map(_.headOption)

    def findById(id: Id[User]) =
      ctx.runIO(users.filter(_.id == lift(id))).map(_.headOption)
  }
}

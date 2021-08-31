package com.stampy.service.modules.org

import com.stampy.service.db.Postgres
import com.stampy.service.domain.{MemberWithUserEmail, Membership, MembershipWithOrgName, Org, Role, User}
import com.stampy.service.modules.user.UserModel
import com.stampy.service.util.Id

trait MembershipModel extends Postgres with UserModel with OrgModel {
  object orgMemberModel {
    import ctx._

    case class MembershipRow(userId: Id[User], orgId: Id[Org], role: Role)
    val assignments = quote(querySchema[MembershipRow]("memberships"))

    def assign(userId: Id[User], membership: Membership) =
      ctx.runIO(assignments.insert(lift(MembershipRow(userId, membership.orgId, membership.role)))) // TODO handle duplicate error

    def findRole(userId: Id[User], orgId: Id[Org]) =
      ctx.runIO(assignments.filter(assignment => assignment.userId == lift(userId) && assignment.orgId == lift(orgId)).map(_.role)).map(_.headOption)

    def remove(userId: Id[User], orgId: Id[Org]) =
      ctx.runIO(assignments.filter(assignment => assignment.userId == lift(userId) && assignment.orgId == lift(orgId)).delete)

    def listByUserIdWithOrgName(userId: Id[User]) =
      ctx.runIO(assignments.filter(assignment => assignment.userId == lift(userId)).join(orgModel.orgs).on(_.orgId == _.id))
        .map(_.toList.map { case (membership, org) => MembershipWithOrgName(org.name, toMembership(membership)) })

    def listByOrgIdWithMemberEmail(orgId: Id[Org]) =
      ctx.runIO(assignments.filter(assignment => assignment.orgId == lift(orgId)).join(userModel.users).on(_.userId == _.id))
        .map(_.toList.map { case (membership, user) => MemberWithUserEmail(user.id, user.email, toMembership(membership)) })


    def unassign(userId: Id[User], orgId: Id[Org]) =
      ctx.runIO(assignments.filter(assignment => assignment.orgId == lift(orgId) && assignment.userId == lift(userId)).delete)

    private def toMembership(row: MembershipRow) = Membership(row.role, row.orgId)
  }
}
package com.stampy.service.modules.org

import com.stampy.service.db.Postgres
import com.stampy.service.domain.{Org, OrgInvite, OrgInviteWithInviteeEmail, OrgInviteWithName, User}
import com.stampy.service.modules.user.UserModel
import com.stampy.service.util.Id

trait OrgInviteModel extends Postgres with OrgModel with UserModel {
  object orgInviteModel {
    import ctx._

    val invites = quote(querySchema[OrgInvite]("org_invites"))

    def create(org: OrgInvite) =
      ctx.runIO(invites.insert(lift(org))).map(_ => org) // TODO handle duplicate error

    def getByUserIdAndOrgId(userId: Id[User], orgId: Id[Org]) =
      ctx.runIO(invites.filter(invite => invite.invitedUserId == lift(userId) && invite.orgId == lift(orgId))).map(_.headOption)

    def listByUserId(userId: Id[User]) =
      ctx.runIO(invites.filter(_.invitedUserId == lift(userId))).map(_.toList)

    def listByUserIdWithOrgName(userId: Id[User]) =
      ctx.runIO(invites.filter(_.invitedUserId == lift(userId)).join(orgModel.orgs).on(_.orgId == _.id))
        .map(_.toList.map { case (invite, org) => OrgInviteWithName(invite, org.name) })

    def listByOrgIdWithInviteeEmail(orgId: Id[Org]) =
      ctx.runIO(invites.filter(_.orgId == lift(orgId)).join(userModel.users).on(_.invitedUserId == _.id))
        .map(_.toList.map { case (invite, user) => OrgInviteWithInviteeEmail(invite, user.email) })

    def delete(orgId: Id[Org], invitedUserId: Id[User]) =
      ctx.runIO(invites.filter(invite => invite.orgId == lift(orgId) && invite.invitedUserId == lift(invitedUserId)).delete)
  }
}
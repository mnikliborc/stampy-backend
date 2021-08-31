package com.stampy.service.modules.org

import com.stampy.service.domain.{MemberRole, Membership}
import com.stampy.service.modules.E2E
import org.junit.Test
import org.scalatest.MustMatchers
import sttp.model.StatusCode

class OrgRoutesSpec extends E2E with MustMatchers {

  @Test
  def shouldRegisterAndGetOwnedOrgs(): Unit = {
    val email = "stamp@stampy.com"
    val orgName = "stampy org"

    val registerResp = user.endpoints.register.send(user.models.User_Register_IN(email))
    val apiKey = registerResp.body.toOption.get.apiKey

    val registerOrgResp = org.endpoints.register.send(apiKey, org.models.Org_Register_IN(orgName, org.models.Card_Template_IN("description", 10, 12)))
    registerOrgResp.code must be(StatusCode(200))
    registerResp.body.isRight must be (true)

    val getOwnedResp = org.endpoints.listOwnedOrgs.send(apiKey)
    getOwnedResp.code must be(StatusCode(200))
    val ownedOrgs = getOwnedResp.body.toOption.get.orgs
    ownedOrgs.map(_.name) must be(List(orgName))
  }

  @Test
  def shouldCreateAndInviteAndReturnListOfInvites(): Unit = {
    val wWorker = withUser()
    val wOwner = withUserAndOrgSelected()

    org.endpoints.createOrgInvite.send(wOwner.apiKey, org.models.Org_Invite_IN(wWorker.user.email.value)).code mustBe StatusCode.Ok
    user.endpoints.listOrgInvites.send(wWorker.apiKey).body.get.invites.map(_.orgInvite.orgId) mustBe List(wOwner.orgId)
  }

  @Test
  def shouldAcceptAndInviteAndBecomeOrgMember(): Unit = {
    val wWorker = withUser()
    val wOwner = withUserAndOrgSelected()
    val orgId = wOwner.orgId

    org.endpoints.createOrgInvite.send(wOwner.apiKey, org.models.Org_Invite_IN(wWorker.user.email.value))
    user.endpoints.listOrgInvites.send(wWorker.apiKey).body.get.invites.map(_.orgInvite.orgId)

    user.endpoints.listOrgMemberships.send(wWorker.apiKey).body.get.memberships mustBe Nil
    org.endpoints.acceptOrgInvite.send(wWorker.apiKey, orgId).code mustBe StatusCode.Ok
    user.endpoints.listOrgMemberships.send(wWorker.apiKey).body.get.memberships.map(_.membership) mustBe List(Membership(MemberRole, orgId))
  }
}
package com.stampy.service.modules

import java.util.UUID

import com.stampy.service.domain.{ApiKey, Org, User}
import com.stampy.service.util.Id

trait Preconditions { self: E2E with Routes =>
  case class WithUser(user: User, apiKey: Id[ApiKey])
  case class WithUserAndOrg(user: User, apiKey: Id[ApiKey], orgId: Id[Org])

  implicit class EitherOps[A, B](e: Either[A, B]) {
    def get: B = e.getOrElse(throw new Exception)
  }

  def randomString = UUID.randomUUID().toString
  val cardTemplate = org.models.Card_Template_IN("description", 10, 12)

  def withUser(): WithUser = {
    val apiKey = user.endpoints.register.send(user.models.User_Register_IN(randomString + "@stampy.com")).body.get.apiKey
    WithUser(user.endpoints.getSelf.send(apiKey).body.get.user, apiKey)
  }

  def withUserAndOrg(): WithUserAndOrg = {
    val wUser = withUser()
    WithUserAndOrg(wUser.user, wUser.apiKey, org.endpoints.register.send(wUser.apiKey, org.models.Org_Register_IN("org-" + randomString, cardTemplate)).body.get.orgId)
  }

  def withUserAndOrgSelected(): WithUserAndOrg = {
    val wUser = withUser()
    val orgId = org.endpoints.register.send(wUser.apiKey, org.models.Org_Register_IN("org-" + randomString, cardTemplate)).body.get.orgId
    val orgApiKey = org.endpoints.selectOrg.send(wUser.apiKey, orgId).body.get.apiKey
    WithUserAndOrg(wUser.user, orgApiKey, orgId)
  }
}

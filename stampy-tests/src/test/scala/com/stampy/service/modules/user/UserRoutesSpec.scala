package com.stampy.service.modules.user

import com.stampy.service.modules.E2E
import org.junit.Test
import org.scalatest.MustMatchers
import sttp.model.StatusCode

class UserRoutesSpec extends E2E with MustMatchers {
  @Test
  def shouldRegisterAndLoginUser(): Unit = {
    val email = "stamp@stampy.com"

    val registerResp = user.endpoints.register.send(user.models.User_Register_IN(email))
    registerResp.code must be (StatusCode(200))
    registerResp.body.isRight must be (true)

    val loginResp = user.endpoints.login.send(user.models.User_Login_IN(email, Some(10)))
    loginResp.code must be(StatusCode(200))
    registerResp.body.isRight must be (true)
  }
}

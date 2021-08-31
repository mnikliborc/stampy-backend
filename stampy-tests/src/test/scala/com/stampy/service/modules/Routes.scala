package com.stampy.service.modules

trait Routes {
  object user {
    val endpoints = com.stampy.service.modules.user.endpoints
    val models = com.stampy.service.modules.user.models
  }

  object org {
    val endpoints = com.stampy.service.modules.org.endpoints
    val models = com.stampy.service.modules.org.models
  }

  object stamp {
    val endpoints = com.stampy.service.modules.stamp.endpoints
    val models = com.stampy.service.modules.stamp.models
  }
}

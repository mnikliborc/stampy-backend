package com.stampy.service.util

import tsec.common.SecureRandomId

trait IdGenerator {
  def nextId[U](): Id[U]
}

object DefaultIdGenerator extends IdGenerator {
  override def nextId[U](): Id[U] = Id(SecureRandomId.Strong.generate)
}

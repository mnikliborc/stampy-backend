package com.stampy.service.config

case class Sensitive(value: String) extends AnyVal {
  override def toString: String = "***"
}

package com.stampy.service

import java.util.{Base64, Locale}

package object util {
  case class Id[A](value: String) extends AnyVal
  case class LowerCased private[util] (value: String) extends AnyVal

  case class Base64Encoded private[util] (value: String) {
    def decodeBytes = Base64.getDecoder.decode(value)
    def decode = new String(Base64.getDecoder.decode(value))
  }
  object Base64Encoded {
    def fromBase64Unsafe(value: String): Base64Encoded = Base64Encoded(value)
  }

  implicit class RichString(val s: String) extends AnyVal {
    def asId[T]: Id[T] = Id(s)
    def lowerCased: LowerCased = LowerCased(s.toLowerCase(Locale.ENGLISH))
    def base64Encoded: Base64Encoded = Base64Encoded(Base64.getEncoder.encodeToString(s.getBytes))
  }
}

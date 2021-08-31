package com.stampy.service.infrastructure

import com.stampy.service.domain._
import com.stampy.service.util.{Id, LowerCased}
import io.circe.generic.AutoDerivation
import io.circe.{Decoder, Encoder, Printer}

/**
 * Import the members of this object when doing JSON serialisation or deserialisation.
 */
object Codecs extends AutoDerivation {
  val noNullsPrinter: Printer = Printer.noSpaces.copy(dropNullValues = true)

  implicit def idDec[A] = Decoder.decodeString.map(Id[A])
  implicit def idEnc[A] = Encoder.encodeString.contramap[Id[A]](_.value)

  implicit lazy val lowerCasedDec = Decoder.decodeString.map(LowerCased)
  implicit lazy val lowerCasedEnc = Encoder.encodeString.contramap[LowerCased](_.value)

  implicit lazy val RoleDec: Decoder[Role] = Decoder.decodeString.emap {
    case "owner" => Right(OwnerRole)
    case "member" => Right(MemberRole)
    case x => Left(s"unsupported role '$x'")
  }

  implicit lazy val RoleEnc: Encoder[Role] = Encoder.encodeString.contramap[Role] {
    case OwnerRole => "owner"
    case MemberRole => "member"
  }

  // weird, but need to declare it because Role decoding fails in tests (even though encoding works ok)
  implicit lazy val MembershipCodec = io.circe.generic.semiauto.deriveCodec[Membership]

  implicit lazy val CardTemplateVersionDec: Decoder[CardTemplateVersion] = Decoder.decodeInt.map(CardTemplateVersion)
  implicit lazy val CardTemplateVersionEnc: Encoder[CardTemplateVersion] = Encoder.encodeInt.contramap(_.value)
}
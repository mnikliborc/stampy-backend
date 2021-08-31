package com.stampy.service

import java.time.{Clock, Instant}

import com.stampy.service.util.{Id, LowerCased}

package object domain {
  case class ApiKey(
    id: Id[ApiKey],
    userId: Id[User],
    membership: Option[Membership],
    createdOn: Instant,
    validUntil: Instant
  )

  object ApiKey {
    def upgrade(apiKey: ApiKey, membership: Membership, validUntil: Instant)(implicit clock: Clock): ApiKey =
      apiKey.copy(membership = Some(membership), createdOn = clock.instant(), validUntil = validUntil)
  }

  case class Org(
    id: Id[Org],
    ownerId: Id[User],
    name: String,
    createdOn: Instant
  )

  case class User(
    id: Id[User],
    email: LowerCased,
    createdOn: Instant
  )

  sealed trait Permission
  object Permission {
    trait Owner extends Permission
    trait Member extends Permission

    object Owner extends Owner
    object Member extends Member
  }


  sealed trait Role { def permissions: Set[Permission] }
    case object OwnerRole extends Role { val permissions = Set(Permission.Owner, Permission.Member) }
    case object MemberRole extends Role { val permissions = Set(Permission.Member) }

  case class Membership(role: Role, orgId: Id[Org])
  case class MemberWithUserEmail(userId: Id[User], userEmail: LowerCased, membership: Membership)
  case class MembershipWithOrgName(orgName: String, membership: Membership)

  case class OrgInvite(
    invitedUserId: Id[User],
    orgId: Id[Org],
    createdOn: Instant
  )
  case class OrgInviteWithName(orgInvite: OrgInvite, orgName: String)
  case class OrgInviteWithInviteeEmail(orgInvite: OrgInvite, inviteeEmail: LowerCased)

  case class CardTemplateVersion(value: Int) extends AnyVal
  case class CardTemplate(orgId: Id[Org], version: CardTemplateVersion, description: String, size: Int, cardExpiresInMonths: Int, createdOn: Instant)

  case class Stamp(
    id: Id[Stamp],
    issuerId: Id[User],
    orgId: Id[Org],
    cardTemplateVersion: CardTemplateVersion,
    createdOn: Instant,
    registeredOn: Instant,
    claimedOn: Option[Instant]
  )

  case class StampPacket(
    id: Id[StampPacket],
    orgId: Id[Org],
    size: Int,
    used: Int,
    createdOn: Instant,
    validUntil: Instant,
  )

  object StampPacket {
    // using Math.max to account for race condition when doing `stampPacket.Model.incrementUsedStamps`
    // we don't care if the customer uses free stamp in this case
    def availableStamps(packets: List[StampPacket]): Int =
      packets.map(p => Math.max(p.size - p.used, 0)).sum
  }
}

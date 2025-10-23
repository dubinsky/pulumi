package org.podval.tools.besom

import besom.Pulumi.PulumiInterpolationOps
import besom.{Context, Output}
import besom.api.gcp.cloudidentity.inputs.{GroupGroupKeyArgs, GroupMembershipPreferredMemberKeyArgs, GroupMembershipRoleArgs}
import besom.api.gcp.cloudidentity.{Group as GroupGCP, GroupArgs, GroupMembership, GroupMembershipArgs}

final class Group(
  name: String,
  displayName: String,
  description: String,
  owners: Seq[User],
  members: Seq[User],
  organizationRoles: Seq[String]
)(using ctx: Context, gcp: Gcp) extends WithResources:
  // pulumi import "gcp:cloudidentity/group:Group" "group:<group email>" "groups/<group id #>"
  // Note: for members - "memberships", unlike Terraform ids where "members" is used!
  // pulumi import "gcp:cloudidentity/groupMembership:GroupMembership" "group:<group email>/user:<email>" "groups/<group id #>/memberships/<user id #>"
  override def resources: Seq[Output[?]] =
    val groupEmail: String = s"$name@${gcp.domain}"
    val group: Output[GroupGCP] = GroupGCP(s"group:$groupEmail", GroupArgs(
      displayName = displayName,
      description = description,
      groupKey = GroupGroupKeyArgs(id = groupEmail),
      labels = groupLabels,
      parent = pulumi"customers/${gcp.directoryCustomerId}"
    ))

    Seq(group) ++ WithResources(Seq(
      OrganizationIam(group, organizationRoles)
    )) ++
    (for user <- owners  yield membership(group, user, List(groupRole("MEMBER"), groupRole("OWNER")))) ++
    (for user <- members yield membership(group, user, List(groupRole("MEMBER"))))

  private val groupLabels: Map[String, String] = Map("cloudidentity.googleapis.com/groups.discussion_forum" -> "")

  private def membership(
    group: Output[GroupGCP],
    user: User,
    roles: List[GroupMembershipRoleArgs]
  )(using ctx: Context): Output[GroupMembership] =
    for
      groupEmail: String <- group.groupKey.id
      result: GroupMembership <- GroupMembership(s"group:$groupEmail/${user.resourceName}", GroupMembershipArgs(
        group = group.id,
        preferredMemberKey = GroupMembershipPreferredMemberKeyArgs(id = user.email),
        roles = roles
      ))
    yield result

  private def groupRole(name: String): GroupMembershipRoleArgs = GroupMembershipRoleArgs(name = name)

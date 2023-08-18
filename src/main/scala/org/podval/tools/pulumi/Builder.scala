package org.podval.tools.pulumi

import com.pulumi.Context
import com.pulumi.core.{Alias, Output}
import com.pulumi.gcp.cloudidentity.inputs.{GroupGroupKeyArgs, GroupMembershipPreferredMemberKeyArgs, GroupMembershipRoleArgs}
import com.pulumi.gcp.cloudidentity.{Group, GroupArgs, GroupMembership, GroupMembershipArgs}
import com.pulumi.gcp.organizations.{IAMMember, IAMMemberArgs, OrganizationsFunctions, Project, ProjectArgs}
import com.pulumi.gcp.organizations.inputs.{GetBillingAccountArgs, GetOrganizationArgs}
import com.pulumi.gcp.organizations.outputs.{GetBillingAccountResult, GetOrganizationResult}
import com.pulumi.gcp.projects.{Service, ServiceArgs, IAMMember as ProjectIAMMember, IAMMemberArgs as ProjectIAMMemberArgs}
import com.pulumi.gcp.serviceAccount.{Account, AccountArgs}
import com.pulumi.gcp.storage.inputs.BucketWebsiteArgs
import com.pulumi.gcp.storage.{Bucket, BucketArgs, BucketIAMMember, BucketIAMMemberArgs}
import com.pulumi.resources.CustomResourceOptions
import scala.jdk.CollectionConverters.*
import scala.util.chaining.*

class Builder(
  context: Context,
  val domain: String,
  val billingAccountDisplayName: String
):
  val organization: Output[GetOrganizationResult] = OrganizationsFunctions.getOrganization(GetOrganizationArgs.builder
    .domain(domain)
    .build
  )

  val billingAccount: Output[GetBillingAccountResult] = OrganizationsFunctions.getBillingAccount(GetBillingAccountArgs.builder
    .displayName(billingAccountDisplayName)
    .open(true)
    .build
  )

  context.`export`("billingAccount", billingAccount.applyValue(_.id))
  context.`export`("directoryCustomerId", organization.applyValue(_.directoryCustomerId))
  context.`export`("orgId", organization.applyValue(_.orgId))

  // pulumi import "gcp:organizations/project:Project" "project:<project id>" "projects/<project id>"
  // pulumi import "gcp:projects/service:Service" "project:<project id>/service:<service>" "<project id>/<service>.googleapis.com"
  def project(
    id: String,
    displayName: String,
    labels: Map[String, String] = Map.empty,
    autoCreateNetwork: Boolean = true
  )(services: String*): Project =
    val project: Project = new Project(s"project:$id",
      ProjectArgs.builder
        .orgId(organization.applyValue(_.orgId))
        .billingAccount(billingAccount.applyValue(_.id))
        .projectId(id)
        .name(displayName)
        .autoCreateNetwork(autoCreateNetwork)
        .pipe(builder => if labels.isEmpty then builder else builder.labels(labels.asJava))
        .build
    )

    for service: String <- services yield new Service(s"${project.pulumiResourceName}/service:$service",
      ServiceArgs.builder
        .project(project.projectId)
        .service(s"$service.googleapis.com")
        .build
    )

    project

  // pulumi import "gcp:serviceAccount/account:Account" "serviceAccount:<id>@<project id>" "projects/<project id>/serviceAccounts/<id>@<project id>.iam.gserviceaccount.com"
  def serviceAccount(
    project: Project,
    id: String,
    displayName: String,
    description: String
  ): Account =
    val projectId: String = project.pulumiResourceName.drop("project:".length) // TODO yuck...
    new Account(s"serviceAccount:$id@$projectId",
      AccountArgs.builder
        .project(project.projectId)
        .accountId(id)
        .displayName(displayName)
        .description(description)
        .build
    )

  def user(name: String): User = User(name, domain)

  def serviceAccountOrganizationRoles(serviceAccount: Account, roles: String*): Seq[IAMMember] = organizationRoles(
    serviceAccount.pulumiResourceName,
    serviceAccount.member,
    roles*
  )

  def userOrganizationRoles(user: User, roles: String*): Seq[IAMMember] = organizationRoles(
    user.resourceName,
    Output.of(user.resourceName),
    roles*
  )

  def groupOrganizationRoles(group: Group, roles: String*): Seq[IAMMember] = organizationRoles(
    group.pulumiResourceName,
    Output.of(group.pulumiResourceName),
    roles*
  )

  // pulumi import "gcp:organizations/iAMMember:IAMMember" "serviceAccount:terraform@infra/role:<role>" "<ORG ID> roles/<role> serviceAccount:terraform@<INFRA PROJECT ID>.iam.gserviceaccount.com"
  // pulumi import "gcp:organizations/iAMMember:IAMMember" "user:<email>/role:<role>" "<ORG ID> roles/<role> user:<email>"
  // pulumi import "gcp:organizations/iAMMember:IAMMember" "group:<email>/role:<role>" "<ORG ID> roles/<role> group:<email>"
  private def organizationRoles(name: String, member: Output[String], roles: String*): Seq[IAMMember] =
    for role: String <- roles yield new IAMMember(s"$name/role:$role",
      IAMMemberArgs.builder
        .orgId(organization.applyValue(_.orgId))
        .member(member)
        .role(s"roles/$role")
        .build
    )

  def serviceAccountProjectRoles(
    project: Project,
    serviceAccount: Account,
    roles: String*
  ): Seq[ProjectIAMMember] = projectRoles(
    project,
    serviceAccount.pulumiResourceName,
    serviceAccount.member,
    roles *
  )

  def userProjectRoles(
    project: Project,
    user: User,
    roles: String*
  ): Seq[ProjectIAMMember] = projectRoles(
    project,
    user.resourceName,
    Output.of(user.resourceName),
    roles*
  )

  // pulumi import "gcp:projects/iAMMember:IAMMember" "project:<project id>/role:<role>/user:<email>" "<project id> roles/<role> user:<email>"
  // pulumi import "gcp:projects/iAMMember:IAMMember" "project:<project id>/role:<role>/serviceAccount:<email>" "<project id> roles/<role> serviceAccount:<email>"
  private def projectRoles(
    project: Project,
    name: String,
    member: Output[String],
    roles: String*
  ): Seq[ProjectIAMMember] =
    for role: String <- roles yield new ProjectIAMMember(
      s"${project.pulumiResourceName}/role:$role/$name",
      ProjectIAMMemberArgs.builder
        .project(project.projectId)
        .member(member)
        .role(s"roles/$role")
        .build
    )

  // pulumi import "gcp:storage/bucket:Bucket" "<name>" "<name>"
  def bucket(
    project: Project,
    name: String,
    location: String,
    storageClas: String = "STANDARD",
    forceDestroy: Boolean = false,
    notFoundPage: Option[String] = None
  ): Bucket =

    new Bucket(
      name,
      BucketArgs.builder
        .project(project.projectId)
        .name(name) // TODO do I need this?
        .location(location)
        .publicAccessPrevention("inherited")
        .uniformBucketLevelAccess(true)
        .forceDestroy(forceDestroy)
        .pipe(builder => notFoundPage.fold(builder)(notFoundPage => builder
          .website(BucketWebsiteArgs.builder.notFoundPage(notFoundPage).build)
        ))
        .build
    )

  // pulumi import "gcp:storage/bucketIAMMember:BucketIAMMember" "<bucket name>/<role>/<member>" "b/<bucket name> roles/<role> <member>" // <member> - e.g., allUsers
  def bucketIamMember(
    bucket: Bucket,
    role: String,
    memberName: String,
    member: String
  ): BucketIAMMember =
    new BucketIAMMember(
      s"${bucket.pulumiResourceName}/$role/$memberName",
      BucketIAMMemberArgs.builder
        .bucket(bucket.name)
        .role(s"roles/$role")
        .member(member)
        .build
    )

  // pulumi import "gcp:cloudidentity/group:Group" "group:<group email>" "groups/<group id #>"
  // Note: for members - "memberships", unlike Terraform ids where "members" is used!
  // pulumi import "gcp:cloudidentity/groupMembership:GroupMembership" "group:<group email>/user:<email>" "groups/<group id #>/memberships/<user id #>"
  def group(
    name: String,
    displayName: String,
    description: String
  )(owners: User*)(members: User*): Group =
    val groupEmail: String = s"$name@$domain"
    val group: Group = new Group(s"group:$groupEmail",
      GroupArgs.builder
        .displayName(displayName)
        .description(description)
        .groupKey(GroupGroupKeyArgs.builder.id(groupEmail).build)
        .labels(Builder.groupLabels.asJava)
        .parent(organization.applyValue(organization => s"customers/${organization.directoryCustomerId}"))
        .build
    )

    def groupMember(user: User, isOwner: Boolean): GroupMembership =
      new GroupMembership(s"${group.pulumiResourceName}/${user.resourceName}",
        GroupMembershipArgs.builder
          .group(group.id)
          .preferredMemberKey(GroupMembershipPreferredMemberKeyArgs.builder.id(user.email).build)
          .roles((if isOwner then Builder.groupOwnerRoles else Builder.groupMemberRoles).asJava)
          .build
      )

    (for email <- owners  yield groupMember(email, isOwner = true )) ++
    (for email <- members yield groupMember(email, isOwner = false))

    group

object Builder:
  private def alias(name: String) = CustomResourceOptions.builder
    .aliases(Alias.builder.name(name).build)
    .build

  private val protect: CustomResourceOptions = CustomResourceOptions.builder
    .protect(true)
    .build

  private val groupLabels: Map[String, String] = Map("cloudidentity.googleapis.com/groups.discussion_forum" -> "")
  private def groupRole(name: String): GroupMembershipRoleArgs = GroupMembershipRoleArgs.builder.name(name).build
  private val groupMemberRoles: List[GroupMembershipRoleArgs] = List(groupRole("MEMBER"))
  private val groupOwnerRoles: List[GroupMembershipRoleArgs] = List(groupRole("MEMBER"), groupRole("OWNER"))

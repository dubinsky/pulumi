package org.podval.tools.pulumi

import com.pulumi.Context
import com.pulumi.core.Output
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

  // pulumi import "gcp:organizations/project:Project" <resource name> "projects/<project id>"
  // pulumi import "gcp:projects/service:Service" "project:<PROJECT RESOURCE NAME>/service:<SERVICE>" "<project id>/<SERVICE>.googleapis.com"
  def project(
    id: String,
    gcpId: String, // globally unique across GCP
    displayName: String,
    labels: Map[String, String] = Map.empty,
    autoCreateNetwork: Boolean = true
  )(services: String*): Project =
    val project: Project = new Project(s"project:$id",
      ProjectArgs.builder
        .orgId(organization.applyValue(_.orgId))
        .billingAccount(billingAccount.applyValue(_.id))
        .projectId(gcpId)
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

  // pulumi import "gcp:projects/iAMMember:IAMMember" "<project resource name>/<role>/<member>" "<project id> roles/<role> user:<name>@<domain>"
  def projectIamMemberUser(
    project: Project,
    role: String,
    userName: String
  ): ProjectIAMMember =
    new ProjectIAMMember(
      s"${project.pulumiResourceName}/$role/$userName",
      ProjectIAMMemberArgs.builder
        .project(project.projectId)
        .role(s"roles/$role")
        .member(s"user:$userName@$domain")
        .build
    )

  // pulumi import "gcp:serviceAccount/account:Account" "serviceAccount:<id>" "projects/<project id>/serviceAccounts/<id>@<project id>.iam.gserviceaccount.com"
  def serviceAccount(
    project: Project,
    id: String,
    name: String,
    displayName: String,
    description: String
  ): Account = new Account(s"serviceAccount:$name",
    AccountArgs.builder
      .project(project.projectId)
      .accountId(id)
      .displayName(displayName)
      .description(description)
      .build
  )

  def serviceAccountOrganizationRoles(serviceAccount: Account, roles: String*): Seq[IAMMember] = organizationRoles(
    serviceAccount.pulumiResourceName,
    serviceAccount.member,
    roles*
  )

  def userOrganizationRoles(name: String, roles: String*): Seq[IAMMember] = organizationRoles(
    s"user:$name",
    Output.of(s"user:$name@$domain"),
    roles*
  )

  def groupOrganizationRoles(group: Group, roles: String*): Seq[IAMMember] = organizationRoles(
    group.pulumiResourceName,
    Output.of(s"${group.pulumiResourceName}@$domain"),
    roles*
  )

  // pulumi import "gcp:organizations/iAMMember:IAMMember" "serviceAccount:terraform/role:<ROLE>" "<ORG ID> roles/<ROLE> serviceAccount:terraform@<INFRA PROJECT ID>.iam.gserviceaccount.com"
  // pulumi import "gcp:organizations/iAMMember:IAMMember" "user:<name>/role:<ROLE>" "<ORG ID> roles/<ROLE> user:<name>@<domain>" or group:<name>@<domain>
  private def organizationRoles(name: String, member: Output[String], roles: String*): Seq[IAMMember] =
    for role: String <- roles yield new IAMMember(s"$name/role:$role",
      IAMMemberArgs.builder
        .orgId(organization.applyValue(_.orgId))
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

  // pulumi import "gcp:cloudidentity/group:Group" "group:<group name>" "groups/<group id #>"
  // Note: for members - "memberships", unlike Terraform ids where "memebrs" is used!
  // pulumi import "gcp:cloudidentity/groupMembership:GroupMembership" "group:<group name>/user:<email>" "groups/<group id #>/memberships/<user id #>"
  def group(
    name: String,
    displayName: String,
    description: String
  )(owners: String*)(members: String*): Group =
    val group: Group = new Group(s"group:$name",
      GroupArgs.builder
        .displayName(displayName)
        .description(description)
        .groupKey(GroupGroupKeyArgs.builder.id(s"$name@$domain").build)
        .labels(Builder.groupLabels)
        .parent(organization.applyValue(organization => s"customers/${organization.directoryCustomerId}"))
        .build
    )

    def groupMember(email: String, isOwner: Boolean): GroupMembership =
      new GroupMembership(s"${group.pulumiResourceName}/user:$email",
        GroupMembershipArgs.builder
          .group(group.id)
          .preferredMemberKey(GroupMembershipPreferredMemberKeyArgs.builder.id(email).build)
          .roles((if isOwner then Builder.groupOwnerRoles else Builder.groupMemberRoles).asJava)
          .build
      )

    (for email <- owners  yield groupMember(email, isOwner = true )) ++
    (for email <- members yield groupMember(email, isOwner = false))

    group

object Builder:
  private val protect: CustomResourceOptions = CustomResourceOptions.builder
    .protect(true)
    .build

  private val groupLabels: java.util.Map[String, String] = Map("cloudidentity.googleapis.com/groups.discussion_forum" -> "").asJava
  private def groupRole(name: String): GroupMembershipRoleArgs = GroupMembershipRoleArgs.builder.name(name).build
  private val groupMemberRoles: List[GroupMembershipRoleArgs] = List(groupRole("MEMBER"))
  private val groupOwnerRoles: List[GroupMembershipRoleArgs] = List(groupRole("MEMBER"), groupRole("OWNER"))

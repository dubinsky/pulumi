package org.podval.tools.besom

// TODO import besom.zio.!!!
import besom.Pulumi.PulumiInterpolationOps
import besom.api.gcp.cloudidentity.inputs.{GroupGroupKeyArgs, GroupMembershipPreferredMemberKeyArgs, GroupMembershipRoleArgs}
import besom.api.gcp.cloudidentity.{Group, GroupArgs, GroupMembership, GroupMembershipArgs}
import besom.api.gcp.organizations.{IAMMember as OrganizationIAMMember, IAMMemberArgs as OrganizationIAMMemberArgs,
  Project, ProjectArgs}
import besom.api.gcp.projects.{IAMMember as ProjectIAMMember, IAMMemberArgs as ProjectIAMMemberArgs, Service, ServiceArgs}
import besom.api.gcp.serviceAccount.{Account, AccountArgs}
import besom.api.gcp.storage.inputs.BucketWebsiteArgs
import besom.api.gcp.storage.{Bucket, BucketArgs, BucketIAMMember, BucketIAMMemberArgs}
import besom.internal.Exports
import besom.{Context, NonEmptyString, Output, Pulumi}

class Builder(
  context: Context,
  val domain: String,
  val billingAccountDisplayName: String,
  val orgId: Output[String],
  val directoryCustomerId: Output[String],
  val billingAccountId: Output[String]
):
  private given Context = context

  // TODO get* are not yet available in Besom:
//  besom.api.gcp.organizations.getOrganization()
//  val organization: Output[GetOrganizationResult] = OrganizationsFunctions.getOrganization(GetOrganizationArgs.builder
//    .domain(domain)
//    .build
//  )
//  val orgId: Output[String] = ???
//  val directoryCustomerId: Output[String] = ???
//
//  val billingAccount: Output[GetBillingAccountResult] = OrganizationsFunctions.getBillingAccount(GetBillingAccountArgs.builder
//    .displayName(billingAccountDisplayName)
//    .open(true)
//    .build
//  )
//  val billingAccountId: Output[String]
//

  def exports: Exports = Pulumi.exports(
    billingAccount = billingAccountId,
    directoryCustomerId = directoryCustomerId,
    orgId = orgId
  )

  def user(name: String): User = User(name, domain)

  // pulumi import "gcp:organizations/project:Project" "project:<project id>" "projects/<project id>"
  // pulumi import "gcp:projects/service:Service" "project:<project id>/service:<service>" "<project id>/<service>.googleapis.com"
  def project(
    id: String,
    displayName: String,
    labels: Map[String, String] = Map.empty,
    autoCreateNetwork: Boolean = true
  )(serviceNames: String*): Output[Project] =
    val project: Output[Project] = Project(s"project:$id", ProjectArgs(
      orgId = orgId,
      billingAccount = billingAccountId,
      projectId = id,
      name = displayName,
      labels = if labels.isEmpty then None else Some(labels),
      autoCreateNetwork = autoCreateNetwork
    ))

    def mkService(name: String) = Service(s"project:$id/service:$name", ServiceArgs(
      project = id,
      service = s"$name.googleapis.com"
    ))

    for
      project <- project
      _ <- Output.sequence(serviceNames.map(mkService))
    yield
      project

  // pulumi import "gcp:serviceAccount/account:Account" "serviceAccount:<id>@<project id>" "projects/<project id>/serviceAccounts/<id>@<project id>.iam.gserviceaccount.com"
  def serviceAccount(
    project: Output[Project],
    id: String,
    displayName: String,
    description: String
  ): Output[Account] =
    for
      project <- project
      projectId <- project.projectId
      serviceAccount <- Account(s"serviceAccount:$id@$projectId", AccountArgs(
        project = projectId,
        accountId = id,
        displayName = displayName,
        description = description
      ))
    yield
      serviceAccount

  // pulumi import "gcp:storage/bucket:Bucket" "<name>" "<name>"
  def bucket(
    project: Output[Project],
    name: NonEmptyString,
    location: String,
    storageClas: String = "STANDARD",
    forceDestroy: Boolean = false,
    isPublic: Boolean = false,
    notFoundPage: Option[String] = None
  ): Output[Bucket] =

    val result: Output[Bucket] = Bucket(name, BucketArgs(
      project = project.projectId,
      name = name, // TODO do I need this?
      storageClass = storageClas,
      location = location,
      publicAccessPrevention = "inherited",
      uniformBucketLevelAccess = true,
      forceDestroy = forceDestroy,
      website = notFoundPage.map(notFoundPage => BucketWebsiteArgs(
        notFoundPage = notFoundPage
      ))
    ))

    if isPublic then bucketPublic(result)

    result

  // pulumi import "gcp:organizations/iAMMember:IAMMember" "serviceAccount:<id>@<project id>/role:<role>" "<ORG ID> roles/<role> serviceAccount:<id>@<project id>.iam.gserviceaccount.com"
  // pulumi import "gcp:organizations/iAMMember:IAMMember" "user:<email>/role:<role>" "<ORG ID> roles/<role> user:<email>"
  // pulumi import "gcp:organizations/iAMMember:IAMMember" "group:<email>/role:<role>" "<ORG ID> roles/<role> group:<email>"
  def organizationRole[A: Principal](principal: Output[A], role: String): Output[OrganizationIAMMember] =
    for
      principalResourceName <- principal.principalResourceName
      result <- OrganizationIAMMember(s"$principalResourceName/role:$role", OrganizationIAMMemberArgs(
        orgId = orgId,
        member = principal.principalMember,
        role = s"roles/$role"
      ))
    yield
      result

  def organizationRoles[A: Principal](principal: Output[A], roles: String*): Output[Seq[OrganizationIAMMember]] =
    Output.sequence(for role <- roles yield organizationRole(principal, role))

  // pulumi import "gcp:projects/iAMMember:IAMMember" "project:<project id>/role:<role>/user:<email>" "<project id> roles/<role> user:<email>"
  // pulumi import "gcp:projects/iAMMember:IAMMember" "project:<project id>/role:<role>/serviceAccount:<email>" "<project id> roles/<role> serviceAccount:<email>"
  def projectRole[A: Principal](
    project: Output[Project],
    principal: Output[A],
    role: String
  ): Output[ProjectIAMMember] =
    for
      projectId <- project.projectId
      principalResourceName <- principal.principalResourceName
      result <- ProjectIAMMember(
        s"project:$projectId/$principalResourceName/role:$role", ProjectIAMMemberArgs(
          project = project.projectId,
          member = principal.principalMember,
          role = s"roles/$role"
      ))
    yield
      result

  def projectRoles[A: Principal](
    project: Output[Project],
    principal: Output[A],
    roles: String*
  ): Output[Seq[ProjectIAMMember]] =
    Output.sequence(for role: String <- roles yield projectRole(project, principal, role))

  // pulumi import "gcp:storage/bucketIAMMember:BucketIAMMember" "<bucket name>/<role>/<member>" "b/<bucket name> roles/<role> <member>"
  def bucketRole[A: Principal](
    bucket: Output[Bucket],
    principal: Output[A],
    role: String
  ): Output[BucketIAMMember] =
    for
      bucketName <- bucket.name
      principalResourceName <- principal.principalResourceName
      result <- BucketIAMMember(
        s"$bucketName/$principalResourceName/role:$role", BucketIAMMemberArgs(
          bucket = bucket.name,
          member = principal.principalMember,
          role = s"roles/$role"
        ))
    yield result

  def bucketRoles[A: Principal](
    bucket: Output[Bucket],
    principal: Output[A],
    roles: String*
  ): Output[Seq[BucketIAMMember]] =
    Output.sequence(for role: String <- roles yield bucketRole(bucket, principal, role))

  def bucketPublic(bucket: Output[Bucket]): Output[Seq[BucketIAMMember]] = bucketRoles(
    bucket = bucket,
    principal = Output(Principal.AllUsers),
    roles = "storage.objectViewer"
  )

  // pulumi import "gcp:cloudidentity/group:Group" "group:<group email>" "groups/<group id #>"
  // Note: for members - "memberships", unlike Terraform ids where "members" is used!
  // pulumi import "gcp:cloudidentity/groupMembership:GroupMembership" "group:<group email>/user:<email>" "groups/<group id #>/memberships/<user id #>"
  def group(
    name: String,
    displayName: String,
    description: String
  )(owners: User*)(members: User*): Output[Group] =
    val groupEmail: String = s"$name@$domain"
    val group: Output[Group] = Group(s"group:$groupEmail", GroupArgs(
        displayName = displayName,
        description = description,
        groupKey = GroupGroupKeyArgs(id = groupEmail),
        labels = Builder.groupLabels,
        parent = pulumi"customers/$directoryCustomerId"
    ))

    def groupMember(user: User, roles: List[GroupMembershipRoleArgs]): Output[GroupMembership] =
      GroupMembership(s"group:$groupEmail/${user.resourceName}", GroupMembershipArgs(
        group = group.id,
        preferredMemberKey = GroupMembershipPreferredMemberKeyArgs(id = user.email),
        roles = roles
      ))

    for
      group <- group
      _ <- Output.sequence(
        (for email <- owners  yield groupMember(email, groupOwnerRoles )) ++
        (for email <- members yield groupMember(email, groupMemberRoles))
      )
    yield
      group

  private def groupRole(name: String): GroupMembershipRoleArgs = GroupMembershipRoleArgs(name = name)
  private val groupMemberRoles: List[GroupMembershipRoleArgs] = List(groupRole("MEMBER"))
  private val groupOwnerRoles: List[GroupMembershipRoleArgs] = List(groupRole("MEMBER"), groupRole("OWNER"))

object Builder:
//  private def alias(name: String) = CustomResourceOptions.builder
//    .aliases(Alias.builder.name(name).build)
//    .build
//
//  private val protect: CustomResourceOptions = CustomResourceOptions.builder
//    .protect(true)
//    .build

  private val groupLabels: Map[String, String] = Map("cloudidentity.googleapis.com/groups.discussion_forum" -> "")


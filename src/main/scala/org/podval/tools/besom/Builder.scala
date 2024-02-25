package org.podval.tools.besom

import besom.Pulumi.PulumiInterpolationOps
import besom.{Config, Context, NonEmptyString, Output, Stack}
import besom.api.gcp.{Provider, ProviderArgs}
import besom.api.gcp.cloudidentity.inputs.{GroupGroupKeyArgs, GroupMembershipPreferredMemberKeyArgs, GroupMembershipRoleArgs}
import besom.api.gcp.cloudidentity.{Group, GroupArgs, GroupMembership, GroupMembershipArgs}
import besom.api.gcp.organizations.{GetBillingAccountArgs, GetBillingAccountResult,
  GetOrganizationArgs, GetOrganizationResult,
  Project, ProjectArgs, getBillingAccount, getOrganization,
  IamMember as OrganizationIamMember, IamMemberArgs as OrganizationIamMemberArgs}
import besom.api.gcp.projects.{Service, ServiceArgs, IamMember as ProjectIamMember, IamMemberArgs as ProjectIamMemberArgs}
import besom.api.gcp.serviceaccount.{Account, AccountArgs}
import besom.api.gcp.storage.inputs.BucketWebsiteArgs
import besom.api.gcp.storage.{Bucket, BucketArgs, BucketIamMember, BucketIamMemberArgs}

class Builder(
  context: Context,
  val domain: String,
  val billingAccountDisplayName: String
):
  private given Context = context

  val projectId: Output[String] = Config("gcp").requireString("project")
  val provider: Output[Provider] = Provider("provider:gcp", ProviderArgs(project = projectId))
//  private val providerOptions = CustomResourceOptions(provider = provider)

  val organization: Output[GetOrganizationResult] = getOrganization(GetOrganizationArgs(domain = domain))
  val orgId: Output[String] = organization.orgId
  val directoryCustomerId: Output[String] = organization.directoryCustomerId

  val billingAccount: Output[GetBillingAccountResult] = getBillingAccount(GetBillingAccountArgs(displayName = billingAccountDisplayName))
  val billingAccountId: Output[String] = billingAccount.id

  def exports(stack: Stack): Stack = stack.exports(
    domain = domain,
    projectId = projectId,
    orgId = orgId,
    billingAccount = billingAccountId,
    directoryCustomerId = directoryCustomerId
  )

  def user(name: String): User = User(name, domain)
  def userOutput(name: String): Output[User] = Output(User(name, domain))

  // pulumi import "gcp:organizations/project:Project" "project:<project id>" "projects/<project id>"
  def project(
    id: String,
    displayName: String,
    labels: Map[String, String] = Map.empty,
    autoCreateNetwork: Boolean = true
  ): Output[Project] = Project(name = s"project:$id", ProjectArgs(
    orgId = orgId,
    billingAccount = billingAccountId,
    projectId = id,
    name = displayName,
    labels = if labels.isEmpty then None else Some(labels),
    autoCreateNetwork = autoCreateNetwork
  ))
  
  // pulumi import "gcp:projects/service:Service" "project:<project id>/service:<service>" "<project id>/<service>.googleapis.com"
  def projectServices(project: Output[Project], serviceNames: String*): Output[Seq[Service]] =
    Output.sequence(for serviceName <- serviceNames yield projectService(project, serviceName))

  def projectService(project: Output[Project], serviceName: String): Output[Service] = for
    projectId: String <- project.projectId
    result: Service <- Service(s"project:$projectId/service:$serviceName", ServiceArgs(
      project = projectId,
      service = s"$serviceName.googleapis.com"
    ))
  yield result  
  
  // pulumi import "gcp:serviceaccount/account:Account" "serviceAccount:<id>@<project id>" "projects/<project id>/serviceAccounts/<id>@<project id>.iam.gserviceaccount.com"
  def serviceAccount(
    project: Output[Project],
    id: String,
    displayName: String,
    description: String
  ): Output[Account] = for
    projectId <- project.projectId
    result <- Account(s"serviceAccount:$id@$projectId", AccountArgs(
      project = projectId,
      accountId = id,
      displayName = displayName,
      description = description
    ))
  yield result

  // pulumi import "gcp:storage/bucket:Bucket" "<name>" "<name>"
  def bucket(
    project: Output[Project],
    name: NonEmptyString,
    location: String,
    storageClas: String = "STANDARD",
    forceDestroy: Boolean = false,
    notFoundPage: Option[String] = None
  ): Output[Bucket] = Bucket(name, BucketArgs(
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
  
  // pulumi import "gcp:organizations/iAMMember:IAMMember" "serviceAccount:<id>@<project id>/role:<role>" "<ORG ID> roles/<role> serviceAccount:<id>@<project id>.iam.gserviceaccount.com"
  // pulumi import "gcp:organizations/iAMMember:IAMMember" "user:<email>/role:<role>" "<ORG ID> roles/<role> user:<email>"
  // pulumi import "gcp:organizations/iAMMember:IAMMember" "group:<email>/role:<role>" "<ORG ID> roles/<role> group:<email>"
  def organizationRole[A: Principal](principal: Output[A], role: String): Output[OrganizationIamMember] = for
    principalResourceName <- principal.principalResourceName
    result <- OrganizationIamMember(s"$principalResourceName/role:$role", OrganizationIamMemberArgs(
      orgId = orgId,
      member = principal.principalMember,
      role = s"roles/$role"
    ))
  yield result

  def organizationRoles[A: Principal](principal: Output[A], roles: String*): Output[Seq[OrganizationIamMember]] =
    Output.sequence(for role <- roles yield organizationRole(principal, role))

  // pulumi import "gcp:projects/iAMMember:IAMMember" "project:<project id>/role:<role>/user:<email>" "<project id> roles/<role> user:<email>"
  // pulumi import "gcp:projects/iAMMember:IAMMember" "project:<project id>/role:<role>/serviceAccount:<email>" "<project id> roles/<role> serviceAccount:<email>"
  def projectRole[A: Principal](
    project: Output[Project],
    principal: Output[A],
    role: String
  ): Output[ProjectIamMember] = for
    projectId <- project.projectId
    principalResourceName <- principal.principalResourceName
    result <- ProjectIamMember(
      s"project:$projectId/$principalResourceName/role:$role", ProjectIamMemberArgs(
        project = project.projectId,
        member = principal.principalMember,
        role = s"roles/$role"
    ))
  yield result

  def projectRoles[A: Principal](
    project: Output[Project],
    principal: Output[A],
    roles: String*
  ): Output[Seq[ProjectIamMember]] =
    Output.sequence(for role: String <- roles yield projectRole(project, principal, role))

  // pulumi import "gcp:storage/bucketIAMMember:BucketIAMMember" "<bucket name>/<role>/<member>" "b/<bucket name> roles/<role> <member>"
  def bucketRole[A: Principal](
    bucket: Output[Bucket],
    principal: Output[A],
    role: String
  ): Output[BucketIamMember] = for
    bucketName <- bucket.name
    principalResourceName <- principal.principalResourceName
    result <- BucketIamMember(
      s"$bucketName/$principalResourceName/role:$role", BucketIamMemberArgs(
        bucket = bucket.name,
        member = principal.principalMember,
        role = s"roles/$role"
      ))
  yield result

  def bucketRoles[A: Principal](
    bucket: Output[Bucket],
    principal: Output[A],
    roles: String*
  ): Output[Seq[BucketIamMember]] =
    Output.sequence(for role: String <- roles yield bucketRole(bucket, principal, role))

  def bucketPublic(bucket: Output[Bucket]): Output[Seq[BucketIamMember]] = bucketRoles(
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
  ): Output[Group] =
    val groupEmail: String = s"$name@$domain"
    Group(s"group:$groupEmail", GroupArgs(
        displayName = displayName,
        description = description,
        groupKey = GroupGroupKeyArgs(id = groupEmail),
        labels = Builder.groupLabels,
        parent = pulumi"customers/$directoryCustomerId"
    ))

  def groupOwners(group: Output[Group], users: User*): Output[Seq[GroupMembership]] =
    Output.sequence(for user <- users yield groupMembership(group, user, List(groupRole("MEMBER"), groupRole("OWNER"))))
    
  def groupMembers(group: Output[Group], users: User*): Output[Seq[GroupMembership]] =
    Output.sequence(for user <- users yield groupMembership(group, user, List(groupRole("MEMBER"))))

  def groupMembership(
    group: Output[Group],
    user: User, 
    roles: List[GroupMembershipRoleArgs]
  ): Output[GroupMembership] = for
    groupEmail: String <- group.groupKey.id
    result: GroupMembership <- GroupMembership(s"group:$groupEmail/${user.resourceName}", GroupMembershipArgs(
      group = group.id,
      preferredMemberKey = GroupMembershipPreferredMemberKeyArgs(id = user.email),
      roles = roles
    ))
  yield result  
    
  private def groupRole(name: String): GroupMembershipRoleArgs = GroupMembershipRoleArgs(name = name)

object Builder:
//  private def alias(name: String) = CustomResourceOptions.builder
//    .aliases(Alias.builder.name(name).build)
//    .build

  private val groupLabels: Map[String, String] = Map("cloudidentity.googleapis.com/groups.discussion_forum" -> "")


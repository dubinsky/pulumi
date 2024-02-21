package tld.domain.infra

import besom.api.gcp.cloudidentity.Group
import besom.api.gcp.organizations.Project
import besom.api.gcp.serviceAccount.Account
import besom.api.gcp.storage.Bucket
import besom.internal.Exports
import besom.{Context, Output, Pulumi, Stack}
import org.podval.tools.besom.Builder

object MainBesom:
  private val infraProjectServiceNames: Seq[String] = Seq(
    "cloudbilling"        , // "Cloud Billing API"
    "cloudidentity"       , //
    "cloudresourcemanager", // "Cloud Resource Manager API" for project operations
    "iam"                 , // "Identity and Access Management (IAM) API" for Service Account creation
    "iamcredentials"      , // "IAM Service Account Credentials API"
    "logging"             , //
    "monitoring"          , //
    "serviceusage"        , // "Service Usage API" for listing/enabling/disabling services
    "storage"             , //
    "storage-api"         , //
    "storage-component"     //
  )

  private val terraformServiceAccountOrganizationRoles: Seq[String] = Seq(
    "billing.admin",
    "compute.admin",
    "dns.admin",
    "domains.admin",
    "iam.organizationRoleAdmin",
    "iam.serviceAccountAdmin",
    "managedidentities.admin",
    "resourcemanager.organizationAdmin",
    "resourcemanager.folderAdmin",
    "resourcemanager.projectCreator",
    "serviceusage.serviceUsageAdmin",
    "storage.admin"
  )

  private val organizationAdminsGroupOrganizationRoles: Seq[String] = Seq(
    "billing.admin",
    "cloudasset.owner",
    "domains.admin",
    "iam.organizationRoleViewer",
    "iam.serviceAccountAdmin",
    "iam.serviceAccountKeyAdmin",
    "oauthconfig.editor",
    "recommender.iamViewer",
    "recommender.projectUtilAdmin",
    "resourcemanager.organizationAdmin",
    "resourcemanager.projectCreator",
    "resourcemanager.projectDeleter",
    "resourcemanager.projectIamAdmin",
    "resourcemanager.projectUndeleter",
    "resourcemanager.projectMover",
    "serviceusage.apiKeysAdmin",
    "serviceusage.serviceUsageAdmin",
    "storage.admin"
  )

  def main(args: Array[String]): Stack = Pulumi.run(mainDo)

  private def mainDo(using context: Context): Stack =
    val builder: Builder = new Builder(
      context = context,
      domain = "domain.tld",
      billingAccountDisplayName = "My Billing Account"
    )

    val infraProject: Output[Project] = builder.project(
      id = "domain-infra",
      displayName = "Domain Cloud Infrastructure"
    )

    val stateBucket: Output[Bucket] = builder.bucket(
      project = infraProject,
      name = "state.domain.tld",
      location = "us-east1"
    )

    val terraformServiceAccount: Output[Account] = builder.serviceAccount(
      project = infraProject,
      id = "terraform",
      displayName = "terraform",
      description = "Service Account for Terraform"
    )

    val organizationAdminsGroup: Output[Group] = builder.group(
      "gcp-organization-admins",
      "gcp-organization-admins",
      "Organization administrators have access to administer all resources belonging to the organization"
    )

    builder.exports(Stack(
      infraProject,
      builder.projectServices(infraProject, infraProjectServiceNames*),
      stateBucket,
      terraformServiceAccount,
      builder.organizationRoles(terraformServiceAccount, terraformServiceAccountOrganizationRoles*),
      organizationAdminsGroup,
      builder.organizationRoles(organizationAdminsGroup, organizationAdminsGroupOrganizationRoles*),
      builder.groupOwners(organizationAdminsGroup, builder.user("admin")),
      classroomTeachersGroup
    ))

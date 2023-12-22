package tld.domain.infra

import com.pulumi.gcp.cloudidentity.Group
import org.podval.tools.pulumi.Builder
import com.pulumi.gcp.organizations.Project
import com.pulumi.gcp.serviceAccount.Account
import com.pulumi.gcp.storage.{Bucket, BucketArgs}
import com.pulumi.{Context, Pulumi}

object MainPulumi:
  def main(args: Array[String]): Unit = Pulumi.run((context: Context) => main(context))

  private def main(context: Context): Unit =
    val builder: Builder = new Builder(
      context = context,
      domain = "domain.tld",
      billingAccountDisplayName = "My Billing Account"
    )

    val infraProject: Project = builder.project(
      id = "domain-infra",
      displayName = "Domain Cloud Infrastructure"
    )(services =
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

    val stateBucket: Bucket = builder.bucket(
      project = infraProject,
      name = "state.domain.tld",
      location = "us-east1"
    )

    val terraformServiceAccount: Account = builder.serviceAccount(
      project = infraProject,
      id = "terraform",
      displayName = "terraform",
      description = "Service Account for Terraform"
    )

    builder.organizationRoles(terraformServiceAccount, roles =
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

    val organizationAdminsGroup: Group = builder.group(
      "gcp-organization-admins",
      "gcp-organization-admins",
      "Organization administrators have access to administer all resources belonging to the organization"
    )(
      builder.user("admin")
    )(
    )

    builder.organizationRoles(organizationAdminsGroup,
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

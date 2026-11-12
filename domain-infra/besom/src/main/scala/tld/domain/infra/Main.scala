package tld.domain.infra

import besom.Context
import org.podval.tools.besom.{CloudFlare, DnsZone, Gcp, Group, Project, User, WithResources}

// Subclass Main and fill the hooks. This object does not define main.
// Main.main is Pulumi.run, and application.mainClass names this object.
// The grok-bot key file is Keys/grok-bot-$infraProjectId.json beside the directory
// that contains Pulumi.yaml. pulumi runs this program with that directory as the
// JVM working directory. Keys is not created if it is missing.
object Main extends org.podval.tools.besom.Main(
  domain = "domain.tld",
  infraProjectId = "domain-infra",
  infraProjectName = "Domain Cloud Infrastructure",
  stateBucketLocation = "us-east1",
  cloudFlareAccountId = "cloudflare-account-id"
):
  override def administrators(using gcp: Gcp): Seq[User] = Seq(User.user("admin"))

  override val infraProjectServiceNames: Seq[String] = Seq(
    "cloudbilling", // "Cloud Billing API"
    "cloudidentity",
    "cloudresourcemanager", // "Cloud Resource Manager API" for project operations
    "iam", // "Identity and Access Management (IAM) API" for Service Account creation
    "iamcredentials", // "IAM Service Account Credentials API"
    "logging",
    "monitoring",
    "serviceusage", // "Service Usage API" for listing/enabling/disabling services
    "storage",
    "storage-api",
    "storage-component"
  )

  override val terraformServiceAccountOrganizationRoles: Seq[String] = Seq(
    "billing.admin",
    "compute.admin",
    "iam.organizationRoleAdmin",
    "iam.serviceAccountAdmin",
    "managedidentities.admin",
    "resourcemanager.organizationAdmin",
    "resourcemanager.folderAdmin",
    "resourcemanager.projectCreator",
    "serviceusage.serviceUsageAdmin",
    "storage.admin"
  )

  override val organizationAdminsGroupOrganizationRoles: Seq[String] = Seq(
    "billing.admin",
    "cloudasset.owner",
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

  // Main builds gcp-organization-admins. Additional groups go here.
  override def groups(using
    ctx: Context,
    gcp: Gcp
  ): Seq[Group] = Seq.empty

  override def dnsZones(using
    ctx: Context,
    cloudFlare: CloudFlare
  ): Seq[DnsZone] = Seq(
    DnsZone(
      domain = "domain.tld",
      www = "example.github.io",
      proxied = true,
      alwaysUseHttps = true
    )
  )

  // WithResources forces child Outputs, including one built inside flatMap.
  // A hand-built resource has to pass gcpResourceOptions() or cloudflareResourceOptions().
  // Version literals stay inline in those methods. Besom rejects a val in CustomResourceOptions.
  override def resources(infraProject: Project)(using
    ctx: Context,
    gcp: Gcp
  ): Seq[WithResources] = Seq.empty

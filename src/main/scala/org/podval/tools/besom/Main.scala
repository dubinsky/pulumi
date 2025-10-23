package org.podval.tools.besom

import besom.{Config, Context, Output, Pulumi, Stack}

abstract class Main(
  domain: String,
  infraProjectId: String, // TODO gcp.provider.project.map(_.get)
  infraProjectName: String,
  stateBucketLocation: String,
  cloudFlareAccountId: String,
  billingAccountDisplayName: String = "My Billing Account"
):
  final def main(args: Array[String]): Unit = Pulumi.run(mainDo)

  private def mainDo(using ctx: Context): Stack =
    given gcp: Gcp = Gcp(
      domain = domain, 
      billingAccountDisplayName = billingAccountDisplayName,
      project = Config("gcp").requireString("project")
    )
    
    given cloudFlare: CloudFlare = CloudFlare(
      cloudFlareAccountId
    )

    val infraProject: Project = Project(
      id = infraProjectId,
      displayName = infraProjectName,
      services = infraProjectServiceNames
    )

    val stateBucket: Bucket = Bucket(
      project = infraProject,
      name = s"state.$domain",
      location = stateBucketLocation
    )

    val terraformServiceAccount: ServiceAccount = ServiceAccount(
      project = infraProject,
      id = "terraform",
      displayName = "terraform",
      description = "Service Account for Terraform",
      organizationRoles = terraformServiceAccountOrganizationRoles,
      projectRoles = Seq.empty
    )

    val adminsGroup: Group = Group(
      name = "gcp-organization-admins",
      displayName = "gcp-organization-admins",
      description = "Organization administrators have access to administer all resources belonging to the organization",
      owners = administrators,
      members = Seq.empty,
      organizationRoles = organizationAdminsGroupOrganizationRoles
    )

    val resources: Seq[Output[?]] = Seq(
      gcp.provider,
      cloudFlare.provider
    ) ++
    WithResources(
      Seq(
        stateBucket,
        infraProject,
        terraformServiceAccount,
        adminsGroup
      ) ++
      groups ++
      dnsZones ++
      this.resources(infraProject)
    )

    Stack(resources *)
      .exports(
        domain = gcp.domain,
        project = gcp.provider.project,
        org = gcp.orgId,
        billingAccount = gcp.billingAccountId,
        directoryCustomer = gcp.directoryCustomerId,
        cloudFlareAccount = cloudFlare.accountId
      )

  def administrators(using gcp: Gcp): Seq[User]

  def infraProjectServiceNames: Seq[String]
  def terraformServiceAccountOrganizationRoles: Seq[String]
  def organizationAdminsGroupOrganizationRoles: Seq[String]
  
  def dnsZones(using
    ctx: Context,
    cloudFlare: CloudFlare
  ): Seq[DnsZone]

  def groups(using
    ctx: Context,
    gcp: Gcp
  ): Seq[Group]

  def resources(infraProject: Project)(using
    ctx: Context,
    gcp: Gcp
  ): Seq[WithResources]

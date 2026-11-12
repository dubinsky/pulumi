package org.podval.tools.besom

import besom.{Config, Context, Output, Pulumi, Stack}

abstract class Main(
  domain: String,
  // Stays a String. All three stacks pass the same id as gcp:project. The grok-bot key
  // name uses it outside any flatMap so preview shows the key.
  infraProjectId: String,
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

    // Guest identity. Same organization roles as terraform. The desktop keeps the terraform key.
    val grokBotServiceAccount: ServiceAccount = ServiceAccount(
      project = infraProject,
      id = "grok-bot",
      displayName = "Grok Bot",
      description = "Service account for the cloud-infrastructure guest",
      organizationRoles = terraformServiceAccountOrganizationRoles,
      projectRoles = Seq.empty
    )

    // Keys is a sibling of the project directory. pulumi runs this program with the JVM
    // working directory set to the directory that contains Pulumi.yaml, so Path.of("")
    // is that directory: ~/Podval/infra writes ~/Podval/Keys/grok-bot-podval-infra.json.
    val grokBotKey: ServiceAccountKey = ServiceAccountKey(
      id = s"serviceAccount:grok-bot@$infraProjectId",
      account = grokBotServiceAccount.account,
      keyAdmins = Seq(
        "terraform" -> terraformServiceAccount.account,
        "grok-bot" -> grokBotServiceAccount.account
      ),
      keyFile = KeyFile.grokBotKeyFile(java.nio.file.Path.of(""), infraProjectId)
    )

    val adminsGroup: Group = Group(
      name = "gcp-organization-admins",
      displayName = "gcp-organization-admins",
      description = "Organization administrators have access to administer all resources belonging to the organization",
      owners = administrators,
      members = Seq.empty,
      organizationRoles = organizationAdminsGroupOrganizationRoles
    )

    // Stack receives these Outputs. One built inside flatMap stays in the graph only by being here.
    // Main calls WithResources once here, after groups() and dnsZones() have already run.
    val resources: Seq[Output[?]] = Seq(
      gcp.provider,
      cloudFlare.provider
    ) ++
    WithResources(
      Seq(
        stateBucket,
        infraProject,
        terraformServiceAccount,
        grokBotServiceAccount,
        grokBotKey,
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
        cloudFlareAccount = cloudFlare.accountId,
        // Forces keyFilePath, which is the privateKey.map that writes the JSON.
        grokBotKeyFile = grokBotKey.keyFilePath
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

  // Stays Seq[WithResources] because AlterRebbe is a WithResources returning ProjectIam[ServiceAgent].
  // A ComponentResource parent would change every child URN.
  def resources(infraProject: Project)(using
    ctx: Context,
    gcp: Gcp
  ): Seq[WithResources]

package org.podval.tools.besom

import besom.{Context, CustomResourceOptions, InvokeOptions, Output}
import besom.api.gcp.{Provider, ProviderArgs}
import besom.api.gcp.organizations.{GetBillingAccountArgs, GetOrganizationArgs, GetOrganizationResult,
  getBillingAccount, getOrganization}

// besom-gcp_3:9.0.0-core.0.5 plugin.json. Resources use the default provider, so the
// version has to be on each of them; the provider resource option does not cover them.
val GcpPluginVersion: String = "9.0.0"

def gcpResourceOptions(ignoreChanges: String*): CustomResourceOptions =
  CustomResourceOptions(
    version = "9.0.0",
    ignoreChanges = ignoreChanges
  )

def gcpInvokeOptions: InvokeOptions = InvokeOptions(version = Some(GcpPluginVersion))

final class Gcp(
  val domain: String,
  billingAccountDisplayName: String,
  project: Output[String]
)(using ctx: Context):
  val provider: Output[Provider] = Provider(
    "provider:gcp",
    ProviderArgs(project = project),
    gcpResourceOptions()
  )

  private val organization: Output[GetOrganizationResult] =
    getOrganization(GetOrganizationArgs(domain = domain), gcpInvokeOptions)
  val orgId: Output[String] = organization.orgId
  val directoryCustomerId: Output[String] = organization.directoryCustomerId

  val billingAccountId: Output[String] =
    getBillingAccount(GetBillingAccountArgs(displayName = billingAccountDisplayName), gcpInvokeOptions).id

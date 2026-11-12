package org.podval.tools.besom

import besom.{Context, CustomResourceOptions, InvokeOptions, Output}
import besom.api.gcp.{Provider, ProviderArgs}
import besom.api.gcp.organizations.{GetBillingAccountArgs, GetOrganizationArgs, GetOrganizationResult,
  getBillingAccount, getOrganization}

// besom-gcp_3:9.0.0-core.0.5 plugin.json. Resources use the default provider, so the
// version has to be on each of them; the provider resource option does not cover them.
// Used by gcpInvokeOptions. InvokeOptions.version is a String, so this val is accepted there.
// CustomResourceOptions keeps the "9.0.0" literal, changed in the same commit. Besom rejects a
// val there: NonEmptyString allows only a constant string or a string interpolation.
val GcpPluginVersion: String = "9.0.0"

// AlterRebbe's artifact-registry agent IamMember skips this, so that binding can drift off 9.0.0.
// Its role is "roles/storage.objectViewer". ProjectIam would prepend "roles/" again.
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

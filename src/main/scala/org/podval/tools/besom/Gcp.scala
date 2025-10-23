package org.podval.tools.besom

import besom.{Context, Output}
import besom.api.gcp.{Provider, ProviderArgs}
import besom.api.gcp.organizations.{GetBillingAccountArgs, GetOrganizationArgs, GetOrganizationResult,
  getBillingAccount, getOrganization}

final class Gcp(
  val domain: String,
  billingAccountDisplayName: String,
  project: Output[String]
)(using ctx: Context):
  val provider: Output[Provider] = Provider(
    "provider:gcp",
    ProviderArgs(project = project)
  )

  private val organization: Output[GetOrganizationResult] = getOrganization(GetOrganizationArgs(domain = domain))
  val orgId: Output[String] = organization.orgId
  val directoryCustomerId: Output[String] = organization.directoryCustomerId

  val billingAccountId: Output[String] = getBillingAccount(GetBillingAccountArgs(displayName = billingAccountDisplayName)).id

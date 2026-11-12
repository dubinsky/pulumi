package org.podval.tools.besom

import besom.{Context, CustomResourceOptions, Output}
import besom.api.cloudflare.{Provider, ProviderArgs}
import besom.internal.SpecResourceAlias

// besom-cloudflare_3:6.20.0-core.0.5 plugin.json. Resources use the default provider, so the
// version has to be on each of them; the provider resource option does not cover them.
// No Cloudflare invoke. The "6.20.0" literal stays inline: Besom rejects a val in CustomResourceOptions.
def cloudflareResourceOptions(ignoreChanges: String*): CustomResourceOptions =
  CustomResourceOptions(
    version = "6.20.0",
    ignoreChanges = ignoreChanges
  )

// CNAME replaced by an A: the alias is the old CNAME logical name, and the A is deleteBeforeReplace.
// Not an overload of cloudflareResourceOptions. This file does not format the alias.
// The "6.20.0" literal is written at this call, the same pin as cloudflareResourceOptions.
// NonEmptyString rejects a val or a version parameter.
def cloudflareReplaceCnameOptions(aliasName: String): CustomResourceOptions =
  CustomResourceOptions(
    version = "6.20.0",
    aliases = SpecResourceAlias(name = Some(aliasName)),
    deleteBeforeReplace = true
  )

final class CloudFlare(
  val accountId: String
)(using ctx: Context):
  val provider: Output[Provider] = Provider(
    "provider:cloudflare",
    ProviderArgs(),
    cloudflareResourceOptions()
  )

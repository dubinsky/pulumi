package org.podval.tools.besom

import besom.{Context, Output}
import besom.api.cloudflare.{Provider, ProviderArgs}

final class CloudFlare(
  val accountId: String
)(using ctx: Context):
  val provider: Output[Provider] = Provider(
    "provider:cloudflare",
    ProviderArgs()
  )

package org.podval.tools.besom

import besom.{Context, Output, opts}
import besom.internal.SpecResourceAlias
import besom.api.cloudflare.{DnsRecord, DnsRecordArgs, Ruleset, RulesetArgs, Zone, ZoneArgs, ZoneDnssec, ZoneDnssecArgs,
  ZoneSetting, ZoneSettingArgs}
import besom.api.cloudflare.inputs.{DnsRecordDataArgs, RulesetRuleActionParametersArgs,
  RulesetRuleActionParametersFromValueArgs, RulesetRuleActionParametersFromValueTargetUrlArgs, RulesetRuleArgs,
  ZoneAccountArgs}
import besom.json.{JsNumber, JsString}
import besom.types.ResourceId

object DnsZone:
  val GOOGLEHOSTED: String = "ghs.googlehosted.com"
  val BUCKET: String = "c.storage.googleapis.com"

  /** Auxiliary domain: orange-cloud dummy A for `@` and `www`, 301 to `https://$canonical`. */
  def alias(
    domain: String,
    canonical: String
  )(using Context, CloudFlare): DnsZone =
    DnsZone(domain = domain, www = canonical, alias = true)

open class DnsZone(
  domain: String,
  www: String,
  cnames: Seq[(String, String)] = Seq.empty,
  dkim: Option[String] = None,
  /** Orange-cloud apex (`@`) and `www`. Extra `cnames` stay DNS-only. */
  proxied: Boolean = false,
  /** Always Use HTTPS. Emitted only when `proxied`, `alwaysUseHttps`, or `alias` is set. */
  alwaysUseHttps: Boolean = false,
  /** Auxiliary zone: do not CNAME to GitHub; redirect `@` and `www` to `www`. */
  alias: Boolean = false
)(using ctx: Context, cloudFlare: CloudFlare) extends WithResources:

  // pulumi import cloudflare:index/zone:Zone zone:<zone name> <zone id>
  private val zone: Output[Zone] = Zone(
    name = s"zone:$domain",
    args = ZoneArgs(
      account = ZoneAccountArgs(id = cloudFlare.accountId),
      name = domain
    )
  )

  private val zoneId: Output[ResourceId] = zone.id

  final override def resources: Seq[Output[?]] =
    // see https://support.google.com/a/answer/16004259
    // old MX records:
    //  1 aspmx.l.google.com
    //  5 alt1.aspmx.l.google.com
    //  5 alt2.aspmx.l.google.com
    //  10 alt3.aspmx.l.google.com
    //  10 alt4.aspmx.l.google.com
    val mail: Seq[Output[DnsRecord]] = dkim.fold(
      // No Workspace mail: refuse spoofing on unused names.
      Seq(
        record("TXT", domain, """"v=spf1 -all""""),
        record("TXT", "_dmarc", """"v=DMARC1; p=reject"""")
      )
    )(dkim => Seq(
      record("TXT", domain, """"v=spf1 include:_spf.google.com ~all""""),
      record("TXT", "google._domainkey", dkim),
      record("MX" , "@", "smtp.google.com", priority = Some(1)),
      record("TXT", "_dmarc", """"v=DMARC1; p=none"""")
    ))

    val extraCnames: Seq[Output[DnsRecord]] =
      for (name, target) <- this.cnames yield record("CNAME", name, target)

    // ghs.googlehosted.com does not terminate TLS for a CNAME-flattened apex.
    val googleOrigin: Boolean = www == DnsZone.GOOGLEHOSTED

    val apexAndWww: Seq[Output[DnsRecord]] =
      if alias then
        // 192.0.2.1 is TEST-NET-1; orange-cloud so Single Redirects can run.
        Seq(
          record("A", "@", "192.0.2.1", proxied = true, replaceCname = true),
          record("A", "www", "192.0.2.1", proxied = true, replaceCname = true)
        )
      else if googleOrigin then
        Seq(
          record("A", "@", "192.0.2.1", proxied = true, replaceCname = true),
          record("CNAME", "www", www)
        )
      else
        Seq(
          record("CNAME", "@", s"www.$domain", proxied = proxied),
          record("CNAME", "www", www, proxied = proxied)
        )

    val emitHttps: Boolean = proxied || alwaysUseHttps || alias || googleOrigin
    val alwaysHttps: Seq[Output[ZoneSetting]] =
      if emitHttps then
        Seq(ZoneSetting(
          name = s"zoneSetting:$domain:always_use_https",
          args = ZoneSettingArgs(
            zoneId = zoneId,
            settingId = "always_use_https",
            value = JsString(if alwaysUseHttps || alias || googleOrigin then "on" else "off")
          )
        ))
      else
        Seq.empty

    val minTls: Output[ZoneSetting] = ZoneSetting(
      name = s"zoneSetting:$domain:min_tls_version",
      args = ZoneSettingArgs(
        zoneId = zoneId,
        settingId = "min_tls_version",
        value = JsString("1.2")
      )
    )

    val dnssec: Output[ZoneDnssec] = ZoneDnssec(
      name = s"zoneDnssec:$domain",
      args = ZoneDnssecArgs(zoneId = zoneId, status = "active")
    )

    val caa: Seq[Output[DnsRecord]] =
      for
        tag <- Seq("issue", "issuewild")
        ca <- Seq("pki.goog", "letsencrypt.org")
      yield caaRecord(tag, ca)

    val redirects: Seq[Output[Ruleset]] =
      if alias then Seq(redirectRuleset(hosts = Seq(domain, s"www.$domain"), to = www))
      else if googleOrigin then Seq(redirectRuleset(hosts = Seq(domain), to = s"www.$domain"))
      else Seq.empty

    Seq(zone, minTls, dnssec) ++
    alwaysHttps ++
    extraCnames ++
    apexAndWww ++
    redirects ++
    mail ++
    caa

  private def redirectRuleset(hosts: Seq[String], to: String): Output[Ruleset] = Ruleset(
    name = s"ruleset:$domain",
    args = RulesetArgs(
      // Zone entrypoint for this phase is named "default"; renaming ForceNew's the
      // resource and Cloudflare then 400s (only one ruleset per phase).
      name = "default",
      description = s"$domain -> $to",
      kind = "zone",
      zoneId = zoneId,
      phase = "http_request_dynamic_redirect",
      rules = Seq(redirectRule(hosts, to))
    ),
    opts(ignoreChanges = Seq("name"))
  )

  private def redirectRule(hosts: Seq[String], to: String): RulesetRuleArgs = RulesetRuleArgs(
    action = "redirect",
    description = s"${hosts.mkString(", ")} -> $to",
    expression = hosts.map(h => s"""(http.host eq "$h")""").mkString(" or "),
    actionParameters = RulesetRuleActionParametersArgs(
      fromValue = RulesetRuleActionParametersFromValueArgs(
        preserveQueryString = true,
        statusCode = 301,
        targetUrl = RulesetRuleActionParametersFromValueTargetUrlArgs(
          expression = s"""concat("https://$to", http.request.uri.path)"""
        )
      )
    )
  )

  private def caaRecord(tag: String, ca: String): Output[DnsRecord] =
    DnsRecord(
      name = s"dnsRecord:$domain-CAA-$tag-$ca",
      args = DnsRecordArgs(
        zoneId = zoneId,
        `type` = "CAA",
        name = "@",
        data = DnsRecordDataArgs(flags = JsNumber(0), tag = tag, value = ca),
        ttl = 1
      )
    )

  private def record(
    typ: String,
    name: String,
    content: String,
    proxied: Boolean = false,
    ttl: Double = 1,
    priority: Option[Double] = None,
    replaceCname: Boolean = false
  ): Output[DnsRecord] =
    val args: DnsRecordArgs = DnsRecordArgs(
      zoneId = zoneId,
      `type` = typ,
      name = name,
      content = content,
      proxied = proxied,
      ttl = ttl,
      priority = priority
    )
    if replaceCname then
      DnsRecord(
        name = s"dnsRecord:$domain-A-$name",
        args = args,
        opts(
          aliases = SpecResourceAlias(name = Some(s"dnsRecord:$domain-CNAME-$name")),
          deleteBeforeReplace = true
        )
      )
    else
      DnsRecord(name = s"dnsRecord:$domain-$typ-$name", args = args)

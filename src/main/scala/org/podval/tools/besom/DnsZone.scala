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
  // Stacks pass these. The literal is on Names so tests can lock it without loading Besom.
  val GOOGLEHOSTED: String = Names.googleHostedCname
  val BUCKET: String = Names.bucketCname

  /** Exact-path 301. `path` is `http.request.uri.path` (no query). `to` is an absolute URL. */
  final case class PathRedirect(path: String, to: String)

  /** Auxiliary domain: orange-cloud dummy A for `@` and `www`, 301 to `https://$canonical`. */
  def alias(
    domain: String,
    canonical: String
  )(using Context, CloudFlare): DnsZone =
    DnsZone(domain = domain, www = canonical, alias = true)

// Open because PodvalOrgZone extends it. That subclass passes constructor arguments and does not read them.
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
  alias: Boolean = false,
  /** Exact-path 301s, same Single Redirect ruleset as a host redirect (one per phase). */
  pathRedirects: Seq[DnsZone.PathRedirect] = Seq.empty
)(using ctx: Context, cloudFlare: CloudFlare) extends WithResources:

  // pulumi import cloudflare:index/zone:Zone zone:<zone name> <zone id>
  private val zone: Output[Zone] = Zone(
    name = Names.zonePulumiName(domain),
    args = ZoneArgs(
      account = ZoneAccountArgs(id = cloudFlare.accountId),
      name = domain
    ),
    opts = cloudflareResourceOptions()
  )

  private val zoneId: Output[ResourceId] = zone.id

  // CognoMath previews with -Xss8m. Eight zones already overflow Besom Result.runM.
  // Another flatMap per record fails that preview before a diff is printed.
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
        record("TXT", Names.spfDnsName(domain), """"v=spf1 -all""""),
        record("TXT", Names.dmarcDnsName, """"v=DMARC1; p=reject"""")
      )
    )(dkim => Seq(
      record("TXT", Names.spfDnsName(domain), """"v=spf1 include:_spf.google.com ~all""""),
      record("TXT", Names.dkimDnsName, dkim),
      record("MX", Names.mxDnsName, "smtp.google.com", priority = Some(1)),
      record("TXT", Names.dmarcDnsName, """"v=DMARC1; p=none"""")
    ))

    val extraCnames: Seq[Output[DnsRecord]] =
      for (name, target) <- this.cnames yield
        val spec: Names.RecordSpec = Names.extraCname(name, target)
        record(spec.typ, spec.name, spec.content, proxied = spec.proxied, replaceCname = spec.replaceCname)

    // Alias, then www == GOOGLEHOSTED. Records, the host redirect, and Always Use HTTPS share this value.
    val mode: ApexMode = Names.apexMode(alias, www)

    // 192.0.2.1 is TEST-NET-1; orange-cloud so Single Redirects can run.
    val apexAndWww: Seq[Output[DnsRecord]] =
      for spec <- Names.apexAndWwwRecords(domain, www, proxied = proxied, mode) yield
        record(spec.typ, spec.name, spec.content, proxied = spec.proxied, replaceCname = spec.replaceCname)

    val alwaysHttps: Seq[Output[ZoneSetting]] =
      Names.httpsSetting(proxied, alwaysUseHttps, mode) match
        case Some(value) =>
          Seq(ZoneSetting(
            name = Names.zoneSettingPulumiName(domain, "always_use_https"),
            args = ZoneSettingArgs(
              zoneId = zoneId,
              settingId = "always_use_https",
              value = JsString(value)
            ),
            opts = cloudflareResourceOptions()
          ))
        case None =>
          Seq.empty

    val minTls: Output[ZoneSetting] = ZoneSetting(
      name = Names.zoneSettingPulumiName(domain, "min_tls_version"),
      args = ZoneSettingArgs(
        zoneId = zoneId,
        settingId = "min_tls_version",
        value = JsString("1.2")
      ),
      opts = cloudflareResourceOptions()
    )

    val dnssec: Output[ZoneDnssec] = ZoneDnssec(
      name = Names.zoneDnssecPulumiName(domain),
      args = ZoneDnssecArgs(zoneId = zoneId, status = "active"),
      opts = cloudflareResourceOptions()
    )

    val caa: Seq[Output[DnsRecord]] =
      for
        tag <- Seq("issue", "issuewild")
        ca <- Seq("pki.goog", "letsencrypt.org")
      yield caaRecord(tag, ca)

    val hostRedirect: Option[(RulesetRuleArgs, String)] =
      Names.hostRedirectFor(domain, www, mode).map { spec =>
        (redirectRule(spec.hosts, spec.to), Names.hostRulesetDescription(domain, spec.to))
      }

    val pathRules: Seq[RulesetRuleArgs] = pathRedirects.map(pathRedirectRule)
    val redirectRules: Seq[RulesetRuleArgs] = pathRules ++ hostRedirect.map(_._1)
    val redirects: Seq[Output[Ruleset]] =
      if redirectRules.isEmpty then Seq.empty
      else
        val description: String =
          val host: Option[String] = hostRedirect.map(_._2)
          val paths: Option[String] = Option.when(pathRules.nonEmpty)(s"$domain path redirects")
          (host.toSeq ++ paths.toSeq).mkString("; ")
        Seq(redirectRuleset(redirectRules, description))

    Seq(zone, minTls, dnssec) ++
    alwaysHttps ++
    extraCnames ++
    apexAndWww ++
    redirects ++
    mail ++
    caa

  private def redirectRuleset(rules: Seq[RulesetRuleArgs], description: String): Output[Ruleset] = Ruleset(
    name = Names.rulesetPulumiName(domain),
    args = RulesetArgs(
      // Zone entrypoint for this phase is named "default"; renaming ForceNew's the
      // resource and Cloudflare then 400s (only one ruleset per phase).
      name = Names.rulesetEntrypointName,
      description = description,
      kind = Names.rulesetKind,
      zoneId = zoneId,
      phase = Names.rulesetPhase,
      rules = rules
    ),
    cloudflareResourceOptions(Names.ignoreRulesetName)
  )

  private def pathRedirectRule(redirect: DnsZone.PathRedirect): RulesetRuleArgs = RulesetRuleArgs(
    action = "redirect",
    description = Names.pathRuleDescription(redirect.path, redirect.to),
    expression = Names.pathMatchExpression(redirect.path),
    actionParameters = RulesetRuleActionParametersArgs(
      fromValue = RulesetRuleActionParametersFromValueArgs(
        preserveQueryString = Names.redirectPreserveQueryString,
        statusCode = Names.redirectStatusCode,
        targetUrl = RulesetRuleActionParametersFromValueTargetUrlArgs(
          value = Names.pathRedirectTargetValue(redirect.to)
        )
      )
    )
  )

  private def redirectRule(hosts: Seq[String], to: String): RulesetRuleArgs = RulesetRuleArgs(
    action = "redirect",
    description = Names.hostRuleDescription(hosts, to),
    expression = Names.hostMatchExpression(hosts),
    actionParameters = RulesetRuleActionParametersArgs(
      fromValue = RulesetRuleActionParametersFromValueArgs(
        preserveQueryString = Names.redirectPreserveQueryString,
        statusCode = Names.redirectStatusCode,
        targetUrl = RulesetRuleActionParametersFromValueTargetUrlArgs(
          expression = Names.hostRedirectTargetExpression(to)
        )
      )
    )
  )

  private def caaRecord(tag: String, ca: String): Output[DnsRecord] =
    DnsRecord(
      name = Names.caaPulumiName(domain, tag, ca),
      args = DnsRecordArgs(
        zoneId = zoneId,
        `type` = "CAA",
        name = Names.caaDnsName,
        data = DnsRecordDataArgs(flags = JsNumber(0), tag = tag, value = ca),
        ttl = 1
      ),
      opts = cloudflareResourceOptions()
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
        name = Names.replaceCnamePulumiName(domain, name),
        args = args,
        opts(
          // Literal, same pin as cloudflareResourceOptions. Not CloudflarePluginVersion.
          version = "6.20.0",
          aliases = SpecResourceAlias(name = Some(Names.replaceCnameAlias(domain, name))),
          deleteBeforeReplace = true
        )
      )
    else
      DnsRecord(
        name = Names.dnsRecordPulumiName(domain, typ, name),
        args = args,
        opts = cloudflareResourceOptions()
      )

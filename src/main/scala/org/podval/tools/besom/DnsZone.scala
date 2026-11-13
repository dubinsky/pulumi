package org.podval.tools.besom

import besom.{Context, Output}
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

/** `www` is a CNAME target, or `DnsZone.GOOGLEHOSTED`, or (for `DnsZone.alias`) the canonical host.
  * `proxied` does not imply Always Use HTTPS. Google origin emits HTTPS `on` without either flag.
  * Extra CNAMEs stay DNS-only. TTL `1` is Cloudflare automatic.
  * `DnsZone.BUCKET` (`c.storage.googleapis.com`) is a CNAME target for website buckets, not an apex mode.
  */
class DnsZone(
  domain: String,
  www: String,
  cnames: Seq[(String, String)] = Seq.empty,
  dkim: Option[String] = None,
  /** Orange-cloud apex (`@`) and `www`. Extra `cnames` stay DNS-only. */
  proxied: Boolean = false,
  /** Always Use HTTPS. `proxied` does not turn this on. Google origin and `alias` emit `on` without this flag. */
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
  //
  // registrationOrder is when these direct calls register. returnedOrder is the Seq this
  // method returns. They are not the same constraint. zone is only in the returned Seq:
  // Zone(...) already ran in the class body, during dnsZones(). Building along returnedOrder
  // would register the children at the wrong time.
  final override def resources: Seq[Output[?]] =
    // Alias, then www == GOOGLEHOSTED. Records, the host redirect, and Always Use HTTPS share this value.
    // It registers nothing, so it is not a step in registrationOrder.
    val mode: ApexMode = Names.apexMode(alias, www)

    // Called in registrationOrder. Constructors run then, not when the returned Seq is built.
    def outputs(step: ZoneStep): Seq[Output[?]] = step match
      case ZoneStep.Mail =>
        // see https://support.google.com/a/answer/16004259
        // old MX records:
        //  1 aspmx.l.google.com
        //  5 alt1.aspmx.l.google.com
        //  5 alt2.aspmx.l.google.com
        //  10 alt3.aspmx.l.google.com
        //  10 alt4.aspmx.l.google.com
        Names.mailRecords(domain, dkim).map { spec =>
          record(spec)
        }

      case ZoneStep.ExtraCnames =>
        for (name, target) <- this.cnames yield
          record(Names.extraCname(name, target))

      case ZoneStep.ApexAndWww =>
        // 192.0.2.1 is TEST-NET-1; orange-cloud so Single Redirects can run.
        for spec <- Names.apexAndWwwRecords(domain, www, proxied = proxied, mode) yield
          record(spec)

      case ZoneStep.AlwaysHttps =>
        Names.httpsSetting(proxied, alwaysUseHttps, mode) match
          case Some(value) =>
            Seq(zoneSetting(Names.alwaysUseHttpsSettingId, value))
          case None =>
            Seq.empty

      case ZoneStep.MinTls =>
        Seq(zoneSetting(Names.minTlsSettingId, Names.minTlsValue))

      case ZoneStep.Dnssec =>
        Seq(ZoneDnssec(
          name = Names.zoneDnssecPulumiName(domain),
          args = ZoneDnssecArgs(zoneId = zoneId, status = Names.dnssecStatus),
          opts = cloudflareResourceOptions()
        ))

      case ZoneStep.Caa =>
        Names.caaPairs.map { (tag, ca) =>
          caaRecord(tag, ca)
        }

      case ZoneStep.Redirects =>
        val hostRedirect: Option[(RulesetRuleArgs, String)] =
          Names.hostRedirectFor(domain, www, mode).map { spec =>
            (redirectRule(spec.hosts, spec.to), Names.hostRulesetDescription(domain, spec.to))
          }
        val pathRules: Seq[RulesetRuleArgs] = pathRedirects.map(pathRedirectRule)
        val redirectRules: Seq[RulesetRuleArgs] =
          Names.redirectRulesInOrder(pathRules, hostRedirect.map(_._1))
        if redirectRules.isEmpty then Seq.empty
        else
          val description: String =
            Names.rulesetDescription(
              hostRedirect.map(_._2),
              Option.when(pathRules.nonEmpty)(Names.pathRulesetDescription(domain))
            )
          Seq(redirectRuleset(redirectRules, description))

      case ZoneStep.Zone =>
        throw IllegalStateException(
          "zone registers in the DnsZone class body, during dnsZones(), before resources runs"
        )

    val constructed: Map[ZoneStep, Seq[Output[?]]] =
      Names.registrationOrder.map(step => step -> outputs(step)).toMap

    Names.returnedOrder.map {
      case ZoneStep.Zone => Seq(zone)
      case step => constructed(step)
    }.flatten

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

  private def zoneSetting(settingId: String, value: String): Output[ZoneSetting] =
    ZoneSetting(
      name = Names.zoneSettingPulumiName(domain, settingId),
      args = ZoneSettingArgs(
        zoneId = zoneId,
        settingId = settingId,
        value = JsString(value)
      ),
      opts = cloudflareResourceOptions()
    )

  private def caaRecord(tag: String, ca: String): Output[DnsRecord] =
    DnsRecord(
      name = Names.caaPulumiName(domain, tag, ca),
      args = DnsRecordArgs(
        zoneId = zoneId,
        `type` = "CAA",
        name = Names.caaDnsName,
        data = DnsRecordDataArgs(flags = JsNumber(Names.caaFlags), tag = tag, value = ca),
        ttl = Names.cloudflareAutomaticTtl
      ),
      opts = cloudflareResourceOptions()
    )

  private def record(spec: Names.RecordSpec): Output[DnsRecord] =
    record(
      spec.typ,
      spec.name,
      spec.content,
      proxied = spec.proxied,
      ttl = Names.cloudflareAutomaticTtl,
      priority = spec.priority,
      replaceCname = spec.replaceCname
    )

  private def record(
    typ: String,
    name: String,
    content: String,
    proxied: Boolean = false,
    ttl: Double = Names.cloudflareAutomaticTtl,
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
    // Alias and deleteBeforeReplace live in cloudflareReplaceCnameOptions. Google-origin www
    // is replaceCname = false and stays on cloudflareResourceOptions, with no alias.
    // Each branch calls its own inline name. Besom rejects a shared call as a resource name.
    // Tests call replaceCnamePulumiName or dnsRecordPulumiName and do not keep a second if.
    if replaceCname then
      DnsRecord(
        name = Names.replaceCnamePulumiName(domain, name),
        args = args,
        opts = cloudflareReplaceCnameOptions(Names.replaceCnameAlias(domain, name))
      )
    else
      DnsRecord(
        name = Names.dnsRecordPulumiName(domain, typ, name),
        args = args,
        opts = cloudflareResourceOptions()
      )

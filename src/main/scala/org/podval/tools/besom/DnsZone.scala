package org.podval.tools.besom

import besom.{Context, Output}
import besom.api.cloudflare.{DnsRecord, DnsRecordArgs, Zone, ZoneArgs}
import besom.api.cloudflare.inputs.ZoneAccountArgs
import besom.types.ResourceId

object DnsZone:
  val GOOGLEHOSTED: String = "ghs.googlehosted.com"
  val BUCKET: String = "c.storage.googleapis.com"

open class DnsZone(
  domain: String,
  www: String,
  cnames: Seq[(String, String)] = Seq.empty,
  dkim: Option[String] = None
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
    val mail: Seq[Output[DnsRecord]] = dkim.fold(Seq.empty)(dkim => Seq(
      record("TXT", domain, """"v=spf1 include:_spf.google.com ~all""""),
      record("TXT", "google._domainkey", dkim),
      record("MX" , "@", "smtp.google.com", priority = Some(1))
    ))

    // TODO I currently use CNAME records to redirect apex domains to www;
    // this seems to cause issues with the SSL certificates;
    // look into the Google way:
    //
    //Following A and AAAA records are used by Google to redirect named domain to www:
    //A 216.239.36.21
    //A 216.239.38.21
    //A 216.239.34.21
    //A 216.239.32.21
    //
    //AAAA 2001:4860:4802:34::15
    //AAAA 2001:4860:4802:38::15
    //AAAA 2001:4860:4802:36::15
    //AAAA 2001:4860:4802:32::15

    val cnames: Seq[Output[DnsRecord]] =
      for (name, target) <- this.cnames ++ Seq("@" -> s"www.$domain", "www" -> www) yield record("CNAME", name, target)
    
    Seq(zone) ++
    cnames ++
    mail

  private def record(
    typ: String,
    name: String,
    content: String,
    proxied: Boolean = false,
    ttl: Double = 1,
    priority: Option[Double] = None
  ): Output[DnsRecord] = DnsRecord(
    name = s"dnsRecord:$domain-$typ-$name",
    args = DnsRecordArgs(
      zoneId = zoneId,
      `type` = typ,
      name = name,
      content = content,
      proxied = proxied,
      ttl = ttl,
      priority = priority
    )
  )

  //protected def rules: Seq[RulesetRuleArgs] = Seq.empty

  //val rules: Seq[RulesetRuleArgs] = this.rules

  //Option.when(rules.nonEmpty)(this.ruleset(rules)).toList

  // curl "https://api.cloudflare.com/client/v4/zones/<zone id>/rulesets" --request GET --header "Authorization: Bearer $CLOUDFLARE_API_TOKEN"
  // pulumi import cloudflare:index/ruleset:Ruleset ruleset:<zone name> 'zones/<zone id>/<ruleset id>'
  //private def ruleset(rules: Seq[RulesetRuleArgs]): Output[Ruleset] = Ruleset(
  //  name = s"ruleset:$domain",
  //  args = RulesetArgs(
  //    name = "default",
  //    description = "",
  //    kind = "zone",
  //    zoneId = this.zoneId,
  //    phase = "http_request_dynamic_redirect",
  //    rules = rules
  //  )
  //)

  //final protected def recordForRules(
  //  name: String
  //): Output[DnsRecord] = record(
  //  typ = "A",
  //  name = name,
  //  content = "192.0.2.1",
  //  proxied = true,
  //  ttl = 1
  //)

  //final protected def redirect(from: String, to: String) = RulesetRuleArgs(
  //  action = "redirect",
  //  description = s"$from -\u003e $to",
  //  expression = s"(http.host eq \"$from\")",
  //  actionParameters = RulesetRuleActionParametersArgs(fromValue = RulesetRuleActionParametersFromValueArgs(
  //    preserveQueryString = true,
  //    statusCode = 301,
  //    targetUrl = RulesetRuleActionParametersFromValueTargetUrlArgs(
  //      expression = s"concat(\"https://$to\", http.request.uri.path)"
  //    )
  //  ))
  //)
  //
  //final protected def redirectWildcard(from: String, to: String) = RulesetRuleArgs(
  //  action = "redirect",
  //  description = s"$from -\u003e $to",
  //  expression = s"(http.request.full_uri wildcard r\"https://$from/*\")",
  //  actionParameters = RulesetRuleActionParametersArgs(fromValue = RulesetRuleActionParametersFromValueArgs(
  //    preserveQueryString = false,
  //    statusCode = 301,
  //    targetUrl = RulesetRuleActionParametersFromValueTargetUrlArgs(
  //      expression = s"wildcard_replace(http.request.full_uri, r\"https://$from/*\", r\"https://$to/$${1}\")"
  //    )
  //  ))
  //)

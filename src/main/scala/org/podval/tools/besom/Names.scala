package org.podval.tools.besom

// Pulumi logical names and DNS strings. Callers invoke these from the current
// Output position. A new flatMap is not required, and Besom types stay out so tests can lock the text.
private[besom] object Names:
  val googleHostedCname: String = "ghs.googlehosted.com"
  val bucketCname: String = "c.storage.googleapis.com"

  def spfDnsName(domain: String): String = domain
  val mxDnsName: String = "@"
  val caaDnsName: String = "@"
  val dkimDnsName: String = "google._domainkey"
  val dmarcDnsName: String = "_dmarc"

  // inline so Besom sees a string interpolation at the resource `name` argument. A plain call is rejected.
  inline def zonePulumiName(domain: String): String = s"zone:$domain"

  inline def zoneSettingPulumiName(domain: String, settingId: String): String =
    s"zoneSetting:$domain:$settingId"

  inline def zoneDnssecPulumiName(domain: String): String = s"zoneDnssec:$domain"

  inline def caaPulumiName(domain: String, tag: String, ca: String): String =
    s"dnsRecord:$domain-CAA-$tag-$ca"

  inline def dnsRecordPulumiName(domain: String, typ: String, name: String): String =
    s"dnsRecord:$domain-$typ-$name"

  inline def replaceCnamePulumiName(domain: String, name: String): String =
    s"dnsRecord:$domain-A-$name"

  inline def replaceCnameAlias(domain: String, name: String): String =
    s"dnsRecord:$domain-CNAME-$name"

  inline def rulesetPulumiName(domain: String): String = s"ruleset:$domain"
  val rulesetEntrypointName: String = "default"
  val rulesetPhase: String = "http_request_dynamic_redirect"
  val rulesetKind: String = "zone"

  val ignoreInitialGroupConfig: String = "initialGroupConfig"
  val ignoreCreateIgnoreAlreadyExists: String = "createIgnoreAlreadyExists"
  val ignoreRulesetName: String = "name"

  val redirectStatusCode: Int = 301
  val redirectPreserveQueryString: Boolean = true

  def hostMatchExpression(hosts: Seq[String]): String =
    hosts.map(host => s"""(http.host eq "$host")""").mkString(" or ")

  def hostRedirectTargetExpression(to: String): String =
    s"""concat("https://$to", http.request.uri.path)"""

  def pathMatchExpression(path: String): String =
    s"""(http.request.uri.path eq "$path")"""

  def pathRedirectTargetValue(to: String): String = to

  def hostRuleDescription(hosts: Seq[String], to: String): String =
    s"${hosts.mkString(", ")} -> $to"

  def hostRulesetDescription(domain: String, to: String): String =
    s"$domain -> $to"

  def pathRuleDescription(path: String, to: String): String =
    s"$path -> $to"

  def apexMode(alias: Boolean, www: String): ApexMode =
    if alias then ApexMode.Alias
    else if www == googleHostedCname then ApexMode.GoogleOrigin
    else ApexMode.Ordinary

  final case class RecordSpec(
    typ: String,
    name: String,
    content: String,
    proxied: Boolean,
    replaceCname: Boolean,
    priority: Option[Double] = None
  ) derives CanEqual

  private val orangeCloudA: String = "192.0.2.1"

  def apexAndWwwRecords(domain: String, www: String, proxied: Boolean, mode: ApexMode): Seq[RecordSpec] =
    mode match
      case ApexMode.Alias =>
        Seq(
          RecordSpec("A", "@", orangeCloudA, proxied = true, replaceCname = true),
          RecordSpec("A", "www", orangeCloudA, proxied = true, replaceCname = true)
        )
      case ApexMode.GoogleOrigin =>
        Seq(
          RecordSpec("A", "@", orangeCloudA, proxied = true, replaceCname = true),
          RecordSpec("CNAME", "www", www, proxied = false, replaceCname = false)
        )
      case ApexMode.Ordinary =>
        Seq(
          RecordSpec("CNAME", "@", s"www.$domain", proxied = proxied, replaceCname = false),
          RecordSpec("CNAME", "www", www, proxied = proxied, replaceCname = false)
        )

  // Extra CNAMEs stay DNS-only. A GOOGLEHOSTED or BUCKET target does not select an apex mode.
  def extraCname(name: String, target: String): RecordSpec =
    RecordSpec("CNAME", name, target, proxied = false, replaceCname = false)

  final case class HostRedirect(hosts: Seq[String], to: String) derives CanEqual

  def hostRedirectFor(domain: String, www: String, mode: ApexMode): Option[HostRedirect] =
    mode match
      case ApexMode.Alias => Some(HostRedirect(Seq(domain, s"www.$domain"), www))
      case ApexMode.GoogleOrigin => Some(HostRedirect(Seq(domain), s"www.$domain"))
      case ApexMode.Ordinary => None

  // alias || (www == ghs.googlehosted.com) is mode != Ordinary. "on" unless the zone is ordinary and only proxied.
  def httpsSetting(proxied: Boolean, alwaysUseHttps: Boolean, mode: ApexMode): Option[String] =
    val shaped: Boolean = mode != ApexMode.Ordinary
    if proxied || alwaysUseHttps || shaped then
      Some(if alwaysUseHttps || shaped then "on" else "off")
    else
      None

  // When the direct Resource(...) calls inside DnsZone.resources register.
  // Zone is not here. It registered when the DnsZone was constructed, during dnsZones().
  // Redirects is the ruleset; it registers last. Not the Seq `resources` returns.
  val registrationOrder: Seq[ZoneStep] = Seq(
    ZoneStep.Mail,
    ZoneStep.ExtraCnames,
    ZoneStep.ApexAndWww,
    ZoneStep.AlwaysHttps,
    ZoneStep.MinTls,
    ZoneStep.Dnssec,
    ZoneStep.Caa,
    ZoneStep.Redirects
  )

  // What DnsZone.resources returns to Stack. Not registrationOrder.
  // Constructing in this order would register the children at the wrong time,
  // even when the Seq handed to Stack is this one.
  val returnedOrder: Seq[ZoneStep] = Seq(
    ZoneStep.Zone,
    ZoneStep.MinTls,
    ZoneStep.Dnssec,
    ZoneStep.AlwaysHttps,
    ZoneStep.ExtraCnames,
    ZoneStep.ApexAndWww,
    ZoneStep.Redirects,
    ZoneStep.Mail,
    ZoneStep.Caa
  )

  private val spfRefuse: String = """"v=spf1 -all""""
  private val spfGoogle: String = """"v=spf1 include:_spf.google.com ~all""""
  private val dmarcReject: String = """"v=DMARC1; p=reject""""
  private val dmarcNone: String = """"v=DMARC1; p=none""""
  private val mxTarget: String = "smtp.google.com"

  // No Workspace mail: refuse spoofing on unused names.
  // Some DKIM: the caller's TXT, quotes included, is not wrapped again.
  def mailRecords(domain: String, dkim: Option[String]): Seq[RecordSpec] =
    dkim.fold(
      Seq(
        RecordSpec("TXT", spfDnsName(domain), spfRefuse, proxied = false, replaceCname = false),
        RecordSpec("TXT", dmarcDnsName, dmarcReject, proxied = false, replaceCname = false)
      )
    )(content => Seq(
      RecordSpec("TXT", spfDnsName(domain), spfGoogle, proxied = false, replaceCname = false),
      RecordSpec("TXT", dkimDnsName, content, proxied = false, replaceCname = false),
      RecordSpec("MX", mxDnsName, mxTarget, proxied = false, replaceCname = false, priority = Some(1)),
      RecordSpec("TXT", dmarcDnsName, dmarcNone, proxied = false, replaceCname = false)
    ))

  val alwaysUseHttpsSettingId: String = "always_use_https"
  val minTlsSettingId: String = "min_tls_version"
  val minTlsValue: String = "1.2"
  val dnssecStatus: String = "active"
  val caaFlags: Int = 0
  // Cloudflare automatic TTL. CAA passes this Int. record widens it to Double, as `ttl = 1` did.
  val cloudflareAutomaticTtl: Int = 1

  def caaPairs: Seq[(String, String)] =
    for
      tag <- Seq("issue", "issuewild")
      ca <- Seq("pki.goog", "letsencrypt.org")
    yield tag -> ca

  def pathRulesetDescription(domain: String): String = s"$domain path redirects"

  // Host note, then path note. The rules themselves are path, then host.
  def rulesetDescription(hostNote: Option[String], pathNote: Option[String]): String =
    (hostNote.toSeq ++ pathNote.toSeq).mkString("; ")

  def redirectRulesInOrder[A](pathRules: Seq[A], hostRule: Option[A]): Seq[A] =
    pathRules ++ hostRule.toSeq

  inline def serviceAccountPulumiName(id: String, projectId: String): String =
    s"serviceAccount:$id@$projectId"

  // Provider member string for an Account. IAM registration passes principal.principalMember and does not call this.
  // The key id is the Pulumi name, not this string.
  def serviceAccountMemberName(id: String, projectId: String): String =
    s"serviceAccount:$id@$projectId.iam.gserviceaccount.com"

  inline def serviceAccountKeyAdminName(id: String, label: String): String =
    s"$id/keyAdmin:$label"

  inline def serviceAccountKeyName(id: String): String =
    s"$id-key"

  inline def projectPulumiName(id: String): String = s"project:$id"

  inline def projectServiceName(projectId: String, service: String): String =
    s"project:$projectId/service:$service"

  inline def organizationIamName(principalResourceName: String, role: String): String =
    s"$principalResourceName/role:$role"

  inline def projectIamName(projectId: String, principalResourceName: String, role: String): String =
    s"project:$projectId/$principalResourceName/role:$role"

  inline def bucketIamName(bucketName: String, principalResourceName: String, role: String): String =
    s"$bucketName/$principalResourceName/role:$role"

  // OrganizationIam, ProjectIam, and BucketIam prepend roles/. Stacks pass unprefixed ids.
  // ServiceAccountKey and AlterRebbe's agent binding pass a full roles/… string and do not call this.
  def iamRole(role: String): String = s"roles/$role"

  def groupEmailAddress(name: String, domain: String): String = s"$name@$domain"

  inline def groupPulumiName(groupEmail: String): String = s"group:$groupEmail"

  inline def groupMembershipName(groupEmail: String, userResourceName: String): String =
    s"group:$groupEmail/$userResourceName"

  def userEmail(localPart: String, domain: String): String = s"$localPart@$domain"

  def userResourceName(localPart: String, domain: String): String =
    s"user:${userEmail(localPart, domain)}"

  // Not a Pulumi name. Main.grokBotKeysDirectoryName delegates here so a test can lock the match.
  // A test subclass of Main cannot be compiled: Besom is compileOnly, and the abstract members
  // take besom.Context. Main calls this from Path.of in mainDo, before Stack forces the key.
  def grokBotKeysDirectoryName(domain: String): String = domain match
    case "podval.org" => "Podval"
    case "opentorah.org" => "OpenTorah"
    case "cognomath.org" => "CognoMath"
    case other => throw IllegalArgumentException(s"no Keys directory for $other")

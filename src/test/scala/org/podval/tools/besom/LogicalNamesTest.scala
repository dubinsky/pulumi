package org.podval.tools.besom

import org.scalatest.funsuite.AnyFunSuite

final class LogicalNamesTest extends AnyFunSuite:
  test("SPF, MX, CAA, DKIM, and DMARC names"):
    assert(Names.spfDnsName("podval.org") == "podval.org")
    assert(Names.spfDnsName("opentorah.org") == "opentorah.org")
    assert(Names.spfDnsName("cognomath.org") == "cognomath.org")
    assert(Names.mxDnsName == "@")
    assert(Names.caaDnsName == "@")
    assert(Names.dkimDnsName == "google._domainkey")
    assert(Names.dmarcDnsName == "_dmarc")
    assert(Names.dnsRecordPulumiName("podval.org", "TXT", "podval.org") == "dnsRecord:podval.org-TXT-podval.org")
    assert(Names.dnsRecordPulumiName("podval.org", "TXT", "google._domainkey") == "dnsRecord:podval.org-TXT-google._domainkey")
    assert(Names.dnsRecordPulumiName("podval.org", "MX", "@") == "dnsRecord:podval.org-MX-@")
    assert(Names.dnsRecordPulumiName("podval.org", "TXT", "_dmarc") == "dnsRecord:podval.org-TXT-_dmarc")
    assert(Names.caaPulumiName("podval.org", "issue", "pki.goog") == "dnsRecord:podval.org-CAA-issue-pki.goog")
    assert(Names.caaPulumiName("podval.org", "issue", "letsencrypt.org") == "dnsRecord:podval.org-CAA-issue-letsencrypt.org")
    assert(Names.caaPulumiName("podval.org", "issuewild", "pki.goog") == "dnsRecord:podval.org-CAA-issuewild-pki.goog")
    assert(Names.caaPulumiName("podval.org", "issuewild", "letsencrypt.org") == "dnsRecord:podval.org-CAA-issuewild-letsencrypt.org")

  test("zone, zone setting, and DNSSEC logical names"):
    assert(Names.zonePulumiName("podval.org") == "zone:podval.org")
    assert(Names.zonePulumiName("alter-rebbe.org") == "zone:alter-rebbe.org")
    assert(Names.zoneSettingPulumiName("podval.org", "always_use_https") == "zoneSetting:podval.org:always_use_https")
    assert(Names.zoneSettingPulumiName("podval.org", "min_tls_version") == "zoneSetting:podval.org:min_tls_version")
    assert(Names.zoneDnssecPulumiName("cognomath.org") == "zoneDnssec:cognomath.org")

  test("ruleset logical name and args"):
    assert(Names.rulesetPulumiName("podval.dev") == "ruleset:podval.dev")
    assert(Names.rulesetPulumiName("cognomath.org") == "ruleset:cognomath.org")
    assert(Names.rulesetEntrypointName == "default")
    assert(Names.rulesetPhase == "http_request_dynamic_redirect")
    assert(Names.rulesetKind == "zone")

  test("ignoreChanges tokens"):
    assert(Names.ignoreInitialGroupConfig == "initialGroupConfig")
    assert(Names.ignoreCreateIgnoreAlreadyExists == "createIgnoreAlreadyExists")
    assert(Names.ignoreRulesetName == "name")

  test("host redirect expression, target, status, and query flag"):
    assert(Names.hostMatchExpression(Seq("podval.dev", "www.podval.dev")) ==
      """(http.host eq "podval.dev") or (http.host eq "www.podval.dev")""")
    assert(Names.hostMatchExpression(Seq("cognomath.org")) == """(http.host eq "cognomath.org")""")
    assert(Names.hostRedirectTargetExpression("www.podval.org") ==
      """concat("https://www.podval.org", http.request.uri.path)""")
    assert(Names.redirectStatusCode == 301)
    assert(Names.redirectPreserveQueryString == true)

  test("path redirect expression uses targetUrl value"):
    assert(Names.pathMatchExpression("/book.pdf") == """(http.request.uri.path eq "/book.pdf")""")
    assert(Names.pathRedirectTargetValue("https://www.chumashquestions.org/book/book.pdf") ==
      "https://www.chumashquestions.org/book/book.pdf")
    assert(Names.pathRuleDescription("/book.pdf", "https://www.chumashquestions.org/book/book.pdf") ==
      "/book.pdf -> https://www.chumashquestions.org/book/book.pdf")
    assert(Names.redirectStatusCode == 301)
    assert(Names.redirectPreserveQueryString == true)

  test("alias podval.dev rule description and ruleset description"):
    val domain = "podval.dev"
    val to = "www.podval.org"
    val hosts = Seq("podval.dev", "www.podval.dev")
    val mode = Names.apexMode(alias = true, www = to)
    assert(mode == Names.ApexMode.Alias)
    assert(Names.hostRedirectFor(domain, to, mode) ==
      Some(Names.HostRedirect(hosts, to)))
    val ruleDescription = Names.hostRuleDescription(hosts, to)
    val rulesetDescription = Names.hostRulesetDescription(domain, to)
    assert(ruleDescription == "podval.dev, www.podval.dev -> www.podval.org")
    assert(rulesetDescription == "podval.dev -> www.podval.org")
    assert(Names.httpsSetting(proxied = false, alwaysUseHttps = false, mode) == Some("on"))
    assert(Names.apexAndWwwRecords(domain, to, proxied = false, mode) == Seq(
      Names.RecordSpec("A", "@", "192.0.2.1", proxied = true, replaceCname = true),
      Names.RecordSpec("A", "www", "192.0.2.1", proxied = true, replaceCname = true)
    ))
    assert(Names.recordResourceName(domain, "A", "@", replaceCname = true) == "dnsRecord:podval.dev-A-@")
    assert(Names.replaceCnameAlias(domain, "@") == "dnsRecord:podval.dev-CNAME-@")
    assert(Names.recordResourceName(domain, "A", "www", replaceCname = true) == "dnsRecord:podval.dev-A-www")
    assert(Names.replaceCnameAlias(domain, "www") == "dnsRecord:podval.dev-CNAME-www")

  test("OrganizationIam terraform@podval-infra billing.admin"):
    assert(Names.serviceAccountPulumiName("terraform", "podval-infra") == "serviceAccount:terraform@podval-infra")
    assert(Names.organizationIamName("serviceAccount:terraform@podval-infra", "billing.admin") ==
      "serviceAccount:terraform@podval-infra/role:billing.admin")
    assert(Names.serviceAccountMemberName("terraform", "podval-infra") ==
      "serviceAccount:terraform@podval-infra.iam.gserviceaccount.com")
    assert(Names.iamRole("billing.admin") == "roles/billing.admin")

  test("ProjectIam nina.dubinsky keeps the dot"):
    assert(Names.userEmail("nina.dubinsky", "cognomath.org") == "nina.dubinsky@cognomath.org")
    assert(Names.userResourceName("nina.dubinsky", "cognomath.org") == "user:nina.dubinsky@cognomath.org")
    assert(Names.projectIamName(
      "cognomath-firebase",
      "user:nina.dubinsky@cognomath.org",
      "identitytoolkit.viewer"
    ) == "project:cognomath-firebase/user:nina.dubinsky@cognomath.org/role:identitytoolkit.viewer")
    assert(Names.iamRole("identitytoolkit.viewer") == "roles/identitytoolkit.viewer")

  test("BucketIam treasure-hunt on cognomath-games"):
    assert(Names.serviceAccountPulumiName("treasure-hunt", "cognomath-games") ==
      "serviceAccount:treasure-hunt@cognomath-games")
    assert(Names.bucketIamName(
      "treasure-hunt.cognomath.org",
      "serviceAccount:treasure-hunt@cognomath-games",
      "storage.objectAdmin"
    ) == "treasure-hunt.cognomath.org/serviceAccount:treasure-hunt@cognomath-games/role:storage.objectAdmin")
    assert(Names.serviceAccountMemberName("treasure-hunt", "cognomath-games") ==
      "serviceAccount:treasure-hunt@cognomath-games.iam.gserviceaccount.com")
    assert(Names.iamRole("storage.objectAdmin") == "roles/storage.objectAdmin")

  test("google origin cognomath.org www is a DNS-only CNAME"):
    assert(Names.googleHostedCname == "ghs.googlehosted.com")
    val mode = Names.apexMode(alias = false, www = "ghs.googlehosted.com")
    assert(mode == Names.ApexMode.GoogleOrigin)
    assert(Names.httpsSetting(proxied = false, alwaysUseHttps = false, mode) == Some("on"))
    assert(Names.apexAndWwwRecords("cognomath.org", "ghs.googlehosted.com", proxied = false, mode) == Seq(
      Names.RecordSpec("A", "@", "192.0.2.1", proxied = true, replaceCname = true),
      Names.RecordSpec("CNAME", "www", "ghs.googlehosted.com", proxied = false, replaceCname = false)
    ))
    assert(Names.recordResourceName("cognomath.org", "A", "@", replaceCname = true) == "dnsRecord:cognomath.org-A-@")
    assert(Names.replaceCnameAlias("cognomath.org", "@") == "dnsRecord:cognomath.org-CNAME-@")
    assert(Names.recordResourceName("cognomath.org", "CNAME", "www", replaceCname = false) ==
      "dnsRecord:cognomath.org-CNAME-www")
    assert(Names.hostRedirectFor("cognomath.org", "ghs.googlehosted.com", mode) ==
      Some(Names.HostRedirect(Seq("cognomath.org"), "www.cognomath.org")))
    assert(Names.hostRuleDescription(Seq("cognomath.org"), "www.cognomath.org") == "cognomath.org -> www.cognomath.org")
    assert(Names.hostRulesetDescription("cognomath.org", "www.cognomath.org") == "cognomath.org -> www.cognomath.org")
    assertExtra("cognomath.org", "games", "c.storage.googleapis.com", "dnsRecord:cognomath.org-CNAME-games")
    assertExtra("cognomath.org", "treasure-hunt", "c.storage.googleapis.com", "dnsRecord:cognomath.org-CNAME-treasure-hunt")
    assertExtra(
      "cognomath.org",
      "blog",
      "blog.cognomath.org.ghs.googlehosted.com",
      "dnsRecord:cognomath.org-CNAME-blog"
    )

  test("mixed podval.org and alter-rebbe.org extra CNAMEs stay DNS-only"):
    assert(Names.bucketCname == "c.storage.googleapis.com")
    val podvalMode = Names.apexMode(alias = false, www = "dubinsky.github.io")
    assert(podvalMode == Names.ApexMode.Ordinary)
    assert(Names.httpsSetting(proxied = true, alwaysUseHttps = true, podvalMode) == Some("on"))
    assert(Names.apexAndWwwRecords("podval.org", "dubinsky.github.io", proxied = true, podvalMode) == Seq(
      Names.RecordSpec("CNAME", "@", "www.podval.org", proxied = true, replaceCname = false),
      Names.RecordSpec("CNAME", "www", "dubinsky.github.io", proxied = true, replaceCname = false)
    ))
    assert(Names.recordResourceName("podval.org", "CNAME", "@", replaceCname = false) == "dnsRecord:podval.org-CNAME-@")
    assert(Names.recordResourceName("podval.org", "CNAME", "www", replaceCname = false) == "dnsRecord:podval.org-CNAME-www")
    assert(Names.hostRedirectFor("podval.org", "dubinsky.github.io", podvalMode).isEmpty)
    val googleHosted = "ghs.googlehosted.com"
    assertExtra("podval.org", "calendar", googleHosted, "dnsRecord:podval.org-CNAME-calendar")
    assertExtra("podval.org", "docs", googleHosted, "dnsRecord:podval.org-CNAME-docs")
    assertExtra("podval.org", "drive", googleHosted, "dnsRecord:podval.org-CNAME-drive")
    assertExtra("podval.org", "email", googleHosted, "dnsRecord:podval.org-CNAME-email")
    assertExtra("podval.org", "groups", googleHosted, "dnsRecord:podval.org-CNAME-groups")
    assertExtra("podval.org", "hangouts", googleHosted, "dnsRecord:podval.org-CNAME-hangouts")
    assertExtra("podval.org", "mail", googleHosted, "dnsRecord:podval.org-CNAME-mail")
    assertExtra("podval.org", "sheets", googleHosted, "dnsRecord:podval.org-CNAME-sheets")
    assertExtra("podval.org", "slides", googleHosted, "dnsRecord:podval.org-CNAME-slides")
    assertExtra(
      "podval.org",
      "_acme-challenge.ha",
      "_acme-challenge.dap5fvcpscokcbefsgj9vcig9njneqys.ui.nabu.casa",
      "dnsRecord:podval.org-CNAME-_acme-challenge.ha"
    )
    assertExtra(
      "podval.org",
      "ha",
      "dap5fvcpscokcbefsgj9vcig9njneqys.ui.nabu.casa",
      "dnsRecord:podval.org-CNAME-ha"
    )
    assertExtra("podval.org", "dub", "dubinsky.github.io", "dnsRecord:podval.org-CNAME-dub")
    assertExtra("podval.org", "nina", "ghs.google.com", "dnsRecord:podval.org-CNAME-nina")
    assertExtra("podval.org", "ssh", "k39.podval.org", "dnsRecord:podval.org-CNAME-ssh")
    assertExtra("podval.org", "sds", "k39.podval.org", "dnsRecord:podval.org-CNAME-sds")

    val alterRebbeMode = Names.apexMode(alias = false, www = "opentorah.github.io")
    assert(alterRebbeMode == Names.ApexMode.Ordinary)
    assert(Names.apexAndWwwRecords("alter-rebbe.org", "opentorah.github.io", proxied = true, alterRebbeMode) == Seq(
      Names.RecordSpec("CNAME", "@", "www.alter-rebbe.org", proxied = true, replaceCname = false),
      Names.RecordSpec("CNAME", "www", "opentorah.github.io", proxied = true, replaceCname = false)
    ))
    assert(Names.hostRedirectFor("alter-rebbe.org", "opentorah.github.io", alterRebbeMode).isEmpty)
    assertExtra("alter-rebbe.org", "app", googleHosted, "dnsRecord:alter-rebbe.org-CNAME-app")
    assertExtra("alter-rebbe.org", "store", "c.storage.googleapis.com", "dnsRecord:alter-rebbe.org-CNAME-store")
    assertExtra("alter-rebbe.org", "facsimiles", "c.storage.googleapis.com", "dnsRecord:alter-rebbe.org-CNAME-facsimiles")

  test("apex mode tests alias before an exact google hosted www"):
    assert(Names.apexMode(alias = true, www = "ghs.googlehosted.com") == Names.ApexMode.Alias)
    assert(Names.apexMode(alias = false, www = "ghs.googlehosted.com") == Names.ApexMode.GoogleOrigin)
    assert(Names.apexMode(alias = false, www = "ghs.google.com") == Names.ApexMode.Ordinary)
    val blogAsWww = Names.apexMode(alias = false, www = "blog.cognomath.org.ghs.googlehosted.com")
    assert(blogAsWww == Names.ApexMode.Ordinary)
    assert(Names.httpsSetting(proxied = true, alwaysUseHttps = false, blogAsWww) == Some("off"))
    assert(Names.httpsSetting(proxied = false, alwaysUseHttps = false, blogAsWww) == None)
    assert(Names.apexAndWwwRecords(
      "cognomath.org",
      "blog.cognomath.org.ghs.googlehosted.com",
      proxied = false,
      blogAsWww
    ) == Seq(
      Names.RecordSpec("CNAME", "@", "www.cognomath.org", proxied = false, replaceCname = false),
      Names.RecordSpec(
        "CNAME",
        "www",
        "blog.cognomath.org.ghs.googlehosted.com",
        proxied = false,
        replaceCname = false
      )
    ))

  test("grok-bot key names for podval-infra"):
    assert(Names.serviceAccountPulumiName("grok-bot", "podval-infra") == "serviceAccount:grok-bot@podval-infra")
    assert(Names.serviceAccountKeyAdminName("serviceAccount:grok-bot@podval-infra", "terraform") ==
      "serviceAccount:grok-bot@podval-infra/keyAdmin:terraform")
    assert(Names.serviceAccountKeyName("serviceAccount:grok-bot@podval-infra") ==
      "serviceAccount:grok-bot@podval-infra-key")
    assert(Names.serviceAccountMemberName("grok-bot", "podval-infra") ==
      "serviceAccount:grok-bot@podval-infra.iam.gserviceaccount.com")

  test("group, project, and service logical names"):
    assert(Names.groupEmailAddress("info", "cognomath.org") == "info@cognomath.org")
    assert(Names.groupPulumiName("info@cognomath.org") == "group:info@cognomath.org")
    assert(Names.userResourceName("dub", "cognomath.org") == "user:dub@cognomath.org")
    assert(Names.groupMembershipName("info@cognomath.org", "user:dub@cognomath.org") ==
      "group:info@cognomath.org/user:dub@cognomath.org")
    assert(Names.groupEmailAddress("gcp-organization-admins", "podval.org") == "gcp-organization-admins@podval.org")
    assert(Names.groupPulumiName("gcp-organization-admins@podval.org") == "group:gcp-organization-admins@podval.org")
    assert(Names.groupEmailAddress("classroom_teachers", "podval.org") == "classroom_teachers@podval.org")
    assert(Names.projectPulumiName("podval-infra") == "project:podval-infra")
    assert(Names.projectPulumiName("cognomath-firebase") == "project:cognomath-firebase")
    assert(Names.projectPulumiName("cognomath-games") == "project:cognomath-games")
    assert(Names.projectServiceName("podval-infra", "cloudidentity") == "project:podval-infra/service:cloudidentity")
    assert(Names.projectServiceName("cognomath-firebase", "identitytoolkit") ==
      "project:cognomath-firebase/service:identitytoolkit")

  private def assertExtra(domain: String, name: String, target: String, pulumiName: String): Unit =
    val spec = Names.extraCname(name, target)
    assert(spec == Names.RecordSpec("CNAME", name, target, proxied = false, replaceCname = false))
    assert(Names.recordResourceName(domain, spec.typ, spec.name, spec.replaceCname) == pulumiName)

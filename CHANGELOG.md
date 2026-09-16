# Changelog
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.6.0] - 2026-09-16
- fix: `DnsZone` emits `always_use_https` ZoneSetting only when `proxied` or `alwaysUseHttps` is set (skip DNS-only GitHub zones).
- feat: `DnsZone.alias` — orange-cloud dummy A for `@`/`www` and a 301 Single Redirect to the canonical host.
- feat: Google-hosted `www` (`ghs.googlehosted.com`): orange-cloud dummy A on the apex and 301 to `www`, leave `www` DNS-only.
- fix: redirect ruleset is named `default` (zone entrypoint); ignore `name` so existing `redirect` rulesets are not replaced (Cloudflare 400: one ruleset per phase).
- feat: every zone gets `min_tls_version` 1.2, DNSSEC `active`, and CAA `issue`/`issuewild` for `pki.goog` and `letsencrypt.org`.
- feat: Workspace zones get `_dmarc` `p=none` (`rua=mailto:dub@<domain>`); zones without DKIM get SPF `-all` and `_dmarc` `p=reject`.

## [0.5.0] - 2026-09-11
- Gradle: match `xml` (JDK 25, Scala 3.9, foojay, nmcp 1.6.2 in settings, versions 0.61.0, configuration cache).
- dep: Besom 0.5.1, GCP 9.0.0-core.0.5, Cloudflare 6.20.0-core.0.5.
- feat: `DnsZone` `proxied` (orange-cloud apex and `www`) and `alwaysUseHttps` (zone setting).
- fix: ignore GCP `Group.initialGroupConfig` and `GroupMembership.createIgnoreAlreadyExists` so provider upgrades do not replace existing groups.

## [0.2.0] - 2024-07-03
- cleanup: no more raw Pulumi, only Besom
- cleanup: various
- dep: Besom 0.3.2
- dep: Besom GCP 3:7.26.0-core.0.3
- update: Gradle 8.9-rc-1

## [0.1.0] - 2024-02-20
- cleanup: no more Terraform
- cleanup: Builder is more explicit
- chore: dependency updates

## [0.0.4] - 2024-02-18
- feat: Besom support
- docs: Besom support
- fix: Pulumi/Besom dependencies are compileOnly
- Besom 0.2.1 (getOrganization, getBillingAccount)
- Besom GCP Provider 7
- chore: minor cleanup
- chore: dependencies and Gradle update

## [0.0.2] - 2023-08-30
- chore: cleanup
- docs: blog post

## [0.0.1] - 2023-08-24
- initial check-in
- chore: transplanting the blog post

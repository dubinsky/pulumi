package org.podval.tools.besom

// Steps of one DnsZone.
// Names.registrationOrder is when the direct calls inside resources register.
// Names.returnedOrder is the Seq resources returns. They are not the same sequence.
// Zone is only in the returned sequence: Zone(...) already ran in the class body.
// Redirects is the ruleset. It registers last.
private[besom] enum ZoneStep derives CanEqual:
  case Mail
  case ExtraCnames
  case ApexAndWww
  case AlwaysHttps
  case MinTls
  case Dnssec
  case Caa
  case Redirects
  case Zone

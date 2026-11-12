package org.podval.tools.besom

// Alias is tested first, then www == ghs.googlehosted.com. A ghs substring is ordinary.
private[besom] enum ApexMode derives CanEqual:
  case Alias
  case GoogleOrigin
  case Ordinary

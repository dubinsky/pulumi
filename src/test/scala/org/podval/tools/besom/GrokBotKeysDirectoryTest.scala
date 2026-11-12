package org.podval.tools.besom

import org.scalatest.funsuite.AnyFunSuite

// The match Main.grokBotKeysDirectoryName delegates to. Does not construct Main or call main.
// Besom is compileOnly, so a test cannot implement Main's abstract members.
final class GrokBotKeysDirectoryTest extends AnyFunSuite:
  test("the three orgs keep today's Keys directory"):
    assert(Names.grokBotKeysDirectoryName("podval.org") == "Podval")
    assert(Names.grokBotKeysDirectoryName("opentorah.org") == "OpenTorah")
    assert(Names.grokBotKeysDirectoryName("cognomath.org") == "CognoMath")

  test("domain.tld has no Keys directory"):
    val caught = intercept[IllegalArgumentException]:
      Names.grokBotKeysDirectoryName("domain.tld")
    assert(caught.getMessage == "no Keys directory for domain.tld")

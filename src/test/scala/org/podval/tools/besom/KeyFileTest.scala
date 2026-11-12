package org.podval.tools.besom

import org.scalatest.funsuite.AnyFunSuite

import java.nio.file.{Files, Path}

final class KeyFileTest extends AnyFunSuite:
  test("sibling Keys for the three infra checkouts"):
    assert(KeyFile.grokBotKeyFile(Path.of("/home/user/Podval/infra"), "podval-infra").toString ==
      "/home/user/Podval/Keys/grok-bot-podval-infra.json")
    assert(KeyFile.grokBotKeyFile(Path.of("/home/user/OpenTorah/infra"), "opentorah-infra").toString ==
      "/home/user/OpenTorah/Keys/grok-bot-opentorah-infra.json")
    assert(KeyFile.grokBotKeyFile(Path.of("/home/user/CognoMath/infra"), "cognomath-infra").toString ==
      "/home/user/CognoMath/Keys/grok-bot-cognomath-infra.json")

  test("Path.of(\"\") resolves against the working directory"):
    val cwd = Path.of("").toAbsolutePath
    assert(KeyFile.grokBotKeyFile(Path.of(""), "podval-infra").toString ==
      cwd.getParent.resolve("Keys").resolve("grok-bot-podval-infra.json").toString)

  test("a project directory at the filesystem root has no sibling Keys"):
    val caught = intercept[IllegalArgumentException] {
      KeyFile.grokBotKeyFile(Path.of("/"), "podval-infra")
    }
    assert(caught.getMessage == "project directory has no parent: /")

  test("a missing Keys directory is named and is not created"):
    val root = Files.createTempDirectory("grok-bot-keys")
    val keys = root.resolve("Keys")
    val keyFile = keys.resolve("grok-bot-podval-infra.json")
    try
      val caught = intercept[IllegalArgumentException] {
        KeyFile.requireKeysDirectory(keyFile)
      }
      assert(caught.getMessage == s"Keys directory does not exist: $keys")
      assert(Files.exists(keys) == false)
      Files.createDirectory(keys)
      KeyFile.requireKeysDirectory(keyFile)
      assert(Files.exists(keyFile) == false)
    finally
      if Files.exists(keys) then Files.delete(keys)
      Files.deleteIfExists(root)

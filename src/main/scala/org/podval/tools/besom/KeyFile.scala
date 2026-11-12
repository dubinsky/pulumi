package org.podval.tools.besom

import java.nio.file.{Files, Path}

// Besom-free so tests can lock the path. The JVM working directory under pulumi is the
// project directory (Besom runs `gradlew run` there, and JavaExec's workingDir is the
// Gradle project directory), so Path.of("") is the checkout that contains Pulumi.yaml.
private[besom] object KeyFile:
  def grokBotKeyFile(projectDirectory: Path, infraProjectId: String): Path =
    val absolute = projectDirectory.toAbsolutePath
    val parent = absolute.getParent
    if parent == null then
      throw IllegalArgumentException(s"project directory has no parent: $absolute")
    parent.resolve("Keys").resolve(s"grok-bot-$infraProjectId.json")

  // Do not create Keys. A missing directory fails and names the path.
  def requireKeysDirectory(keyFile: Path): Unit =
    val keysDirectory = keyFile.getParent
    if keysDirectory == null || !Files.isDirectory(keysDirectory) then
      val expected = if keysDirectory == null then keyFile else keysDirectory
      throw IllegalArgumentException(s"Keys directory does not exist: $expected")

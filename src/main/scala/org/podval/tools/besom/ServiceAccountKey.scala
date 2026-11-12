package org.podval.tools.besom

import besom.{Context, Output}
import besom.api.gcp.serviceaccount.{Account, IamMember, IamMemberArgs, Key, KeyArgs}

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, StandardCopyOption}
import java.nio.file.attribute.PosixFilePermissions
import java.util.Base64

// JSON key for the cloud-infrastructure guest. terraform@ creates it, so terraform@ needs key admin on this
// account before the key resource. The guest account needs the same role so its own preview can read the key.
// The provider returns the JSON base64-encoded. The file written here is the decoded credentials file.
final class ServiceAccountKey(
  id: String,
  account: Output[Account],
  keyAdmins: Seq[(String, Output[Account])],
  keyFile: Path
)(using ctx: Context) extends WithResources:

  // Register these during preview. A for-comprehension would wait for the account name and the
  // preview would not show the key. gcpServiceAccountKeyOptions passes these as dependsOn, so the
  // key is still created after key admin is granted.
  private val keyAdminMembers: Seq[Output[IamMember]] =
    keyAdmins.map { (label, admin) =>
      IamMember(Names.serviceAccountKeyAdminName(id, label), IamMemberArgs(
        serviceAccountId = account.flatMap(_.name),
        member = admin.flatMap(_.member),
        // Full role id. Names.iamRole would prepend roles/ again.
        role = "roles/iam.serviceAccountKeyAdmin"
      ), gcpResourceOptions())
    }

  val key: Output[Key] =
    Key(Names.serviceAccountKeyName(id), KeyArgs(
      serviceAccountId = account.flatMap(_.name),
      privateKeyType = "TYPE_GOOGLE_CREDENTIALS_FILE"
    ), gcpServiceAccountKeyOptions(keyAdminMembers))

  // Derived from the secret, so Pulumi treats the path as secret too. The value is only the path.
  // The export grokBotKeyFile forces this map.
  val keyFilePath: Output[String] =
    key.flatMap(_.privateKey).map(writeCredentials)

  override def resources: Seq[Output[?]] = keyAdminMembers :+ key

  // Write on every machine, including the cloud-infrastructure guest. That copy is redundant:
  // the copy script places the desktop grok-bot-<project>.json on the guest as
  // terraform-<project>.json, which .envrc uses. This library reads the file only to compare
  // contents and skip an unchanged write. Pulumi itself authenticates with GOOGLE_CREDENTIALS
  // from .envrc, never with this file.
  // Leave the file untouched when it already holds this JSON.
  private def writeCredentials(privateKey: String): String =
    val json = credentialsJson(privateKey)
    KeyFile.requireKeysDirectory(keyFile)
    if KeyFile.shouldWriteCredentials(existingCredentials(keyFile), json) then
      val tmp = keyFile.resolveSibling(keyFile.getFileName.toString + ".tmp")
      Files.writeString(tmp, json)
      Files.setPosixFilePermissions(tmp, PosixFilePermissions.fromString("rw-------"))
      Files.move(tmp, keyFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    keyFile.toString

  private def existingCredentials(path: Path): Option[String] =
    if Files.isRegularFile(path) then Some(Files.readString(path)) else None

  private def credentialsJson(privateKey: String): String =
    val trimmed = privateKey.trim
    val decoded =
      if trimmed.startsWith("{") then trimmed
      else new String(Base64.getMimeDecoder.decode(trimmed), StandardCharsets.UTF_8)
    if !decoded.contains("\"client_email\"") || !decoded.contains("\"private_key\"") then
      throw IllegalArgumentException("service account key was not a credentials JSON file")
    if decoded.endsWith("\n") then decoded else decoded + "\n"

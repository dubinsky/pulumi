package org.podval.tools.besom

import besom.{Context, CustomResourceOptions, Output}
import besom.api.gcp.serviceaccount.{Account, IamMember, IamMemberArgs, Key, KeyArgs}

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, StandardCopyOption}
import java.nio.file.attribute.PosixFilePermissions
import java.util.Base64

// JSON key for the cloud-infrastructure guest. terraform@ creates it, so terraform@ needs key admin on this
// account before the key resource. The guest account needs the same role so its own preview can read the key.
// The provider returns the JSON base64-encoded. The desktop file is the decoded credentials file.
final class ServiceAccountKey(
  id: String,
  account: Output[Account],
  keyAdmins: Seq[(String, Output[Account])],
  keyFile: Path
)(using ctx: Context) extends WithResources:

  // Register these during preview. A for-comprehension would wait for the account name and the
  // preview would not show the key. dependsOn still creates the key after key admin is granted.
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
    ), CustomResourceOptions(
      // Literal, same pin as gcpResourceOptions. GcpPluginVersion is the invoke version.
      version = "9.0.0",
      dependsOn = keyAdminMembers
    ))

  // Derived from the secret, so Pulumi treats the path as secret too. The value is only the path.
  val keyFilePath: Output[String] =
    key.flatMap(_.privateKey).map(writeCredentials)

  override def resources: Seq[Output[?]] = keyAdminMembers :+ key

  private def writeCredentials(privateKey: String): String =
    if !onCloudInfrastructureGuest then
      val json = credentialsJson(privateKey)
      Files.createDirectories(keyFile.getParent)
      val tmp = keyFile.resolveSibling(keyFile.getFileName.toString + ".tmp")
      Files.writeString(tmp, json)
      Files.setPosixFilePermissions(tmp, PosixFilePermissions.fromString("rw-------"))
      Files.move(tmp, keyFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    keyFile.toString

  private def onCloudInfrastructureGuest: Boolean =
    Files.readString(Path.of("/etc/hostname")).trim == "cloud-infrastructure"

  private def credentialsJson(privateKey: String): String =
    val trimmed = privateKey.trim
    val decoded =
      if trimmed.startsWith("{") then trimmed
      else new String(Base64.getMimeDecoder.decode(trimmed), StandardCharsets.UTF_8)
    if !decoded.contains("\"client_email\"") || !decoded.contains("\"private_key\"") then
      throw IllegalArgumentException("service account key was not a credentials JSON file")
    if decoded.endsWith("\n") then decoded else decoded + "\n"

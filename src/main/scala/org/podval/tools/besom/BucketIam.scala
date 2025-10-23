package org.podval.tools.besom

import besom.{Context, Output}
import besom.api.gcp.storage.{BucketIamMember, BucketIamMemberArgs}

// pulumi import "gcp:storage/bucketIAMMember:BucketIAMMember" "<bucket name>/<role>/<member>" "b/<bucket name> roles/<role> <member>"
final class BucketIam[A: Principal](
  bucket: Bucket,
  principal: Output[A],
  role: String
)(using ctx: Context) extends WithResources:
  override def resources: Seq[Output[BucketIamMember]] = Seq(
    for
      bucketName <- bucket.bucket.name
      principalResourceName <- principal.principalResourceName
      result <- BucketIamMember(
        s"$bucketName/$principalResourceName/role:$role", BucketIamMemberArgs(
          bucket = bucketName,
          member = principal.principalMember,
          role = s"roles/$role"
        )
      )
    yield result
  )

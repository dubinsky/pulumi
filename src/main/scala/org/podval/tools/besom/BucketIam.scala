package org.podval.tools.besom

import besom.{Context, Output}
import besom.api.gcp.storage.{BucketIamMember, BucketIamMemberArgs}

// Pulumi name is $bucketName/$principalResourceName/role:$role.
// Non-Account (allUsers, user, group): the name segment and member are the same string.
// pulumi import "gcp:storage/bucketIAMMember:BucketIAMMember" "<bucket>/<member>/role:<role>" "b/<bucket> roles/<role> <member>"
// Account: the name segment is serviceAccount:$id@$projectId. member is principalMember, not that segment.
// pulumi import "gcp:storage/bucketIAMMember:BucketIAMMember" "<bucket>/serviceAccount:<id>@<project id>/role:<role>" "b/<bucket> roles/<role> serviceAccount:<id>@<project id>.iam.gserviceaccount.com"
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
        Names.bucketIamName(bucketName, principalResourceName, role), BucketIamMemberArgs(
          bucket = bucketName,
          member = principal.principalMember,
          role = Names.iamRole(role)
        ), gcpResourceOptions()
      )
    yield result
  )

package org.podval.tools.besom

import besom.{Context, Output}
import besom.api.gcp.organizations.{IamMember, IamMemberArgs}

// pulumi import "gcp:organizations/iAMMember:IAMMember" "serviceAccount:<id>@<project id>/role:<role>" "<ORG ID> roles/<role> serviceAccount:<id>@<project id>.iam.gserviceaccount.com"
// pulumi import "gcp:organizations/iAMMember:IAMMember" "user:<email>/role:<role>" "<ORG ID> roles/<role> user:<email>"
// pulumi import "gcp:organizations/iAMMember:IAMMember" "group:<email>/role:<role>" "<ORG ID> roles/<role> group:<email>"
final class OrganizationIam[A: Principal](
  principal: Output[A], 
  roles: Seq[String]
)(using ctx: Context, gcp: Gcp) extends WithResources:
  override def resources: Seq[Output[IamMember]] =
    for role <- roles yield
      for
        principalResourceName <- principal.principalResourceName
        result <- IamMember(s"$principalResourceName/role:$role", IamMemberArgs(
          orgId = gcp.orgId,
          member = principal.principalMember,
          role = s"roles/$role"
        ))
      yield result

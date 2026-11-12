package org.podval.tools.besom

import besom.{Context, Output}
import besom.api.gcp.projects.{IamMember, IamMemberArgs}

// Pulumi name is project:$projectId/$principalResourceName/role:$role.
// For an Account, the name segment is serviceAccount:$id@$projectId and IamMemberArgs.member
// is principalMember (account.member, …iam.gserviceaccount.com). A User uses the same string for both.
// pulumi import "gcp:projects/iAMMember:IAMMember" "project:<project id>/user:<email>/role:<role>" "<project id> roles/<role> user:<email>"
// pulumi import "gcp:projects/iAMMember:IAMMember" "project:<project id>/serviceAccount:<id>@<project id>/role:<role>" "<project id> roles/<role> serviceAccount:<id>@<project id>.iam.gserviceaccount.com"
final class ProjectIam[A: Principal](
  project: Project,
  principal: Output[A],
  roles: Seq[String]
)(using ctx: Context) extends WithResources:
  override def resources: Seq[Output[?]] =
    for role: String <- roles yield
      for
        projectId <- project.project.projectId
        principalResourceName <- principal.principalResourceName
        result <- IamMember(
          Names.projectIamName(projectId, principalResourceName, role), IamMemberArgs(
            project = projectId,
            member = principal.principalMember,
            role = Names.iamRole(role)
          ), gcpResourceOptions())
      yield result

package org.podval.tools.besom

import besom.{Context, Output}
import besom.api.gcp.projects.{IamMember, IamMemberArgs}

// pulumi import "gcp:projects/iAMMember:IAMMember" "project:<project id>/role:<role>/user:<email>" "<project id> roles/<role> user:<email>"
// pulumi import "gcp:projects/iAMMember:IAMMember" "project:<project id>/role:<role>/serviceAccount:<email>" "<project id> roles/<role> serviceAccount:<email>"
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
          s"project:$projectId/$principalResourceName/role:$role", IamMemberArgs(
            project = projectId,
            member = principal.principalMember,
            role = s"roles/$role"
          ))
      yield result

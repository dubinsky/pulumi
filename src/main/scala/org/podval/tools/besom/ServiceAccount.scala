package org.podval.tools.besom

import besom.api.gcp.serviceaccount.{Account, AccountArgs}
import besom.{Context, Output}

// pulumi import "gcp:serviceaccount/account:Account" "serviceAccount:<id>@<project id>" "projects/<project id>/serviceAccounts/<id>@<project id>.iam.gserviceaccount.com"
final class ServiceAccount(
  project: Project,
  id: String,
  displayName: String,
  description: String,
  organizationRoles: Seq[String],
  projectRoles: Seq[String]
)(using ctx: Context, gcp: Gcp) extends WithResources:

  val account: Output[Account] =
    for
      projectId <- project.project.projectId
      result <- Account(s"serviceAccount:$id@$projectId", AccountArgs(
        project = projectId,
        accountId = id,
        displayName = displayName,
        description = description
      ))
    yield result

  override def resources: Seq[Output[?]] =
    Seq(account) ++ WithResources(Seq(
      OrganizationIam(account, organizationRoles),
      ProjectIam(project, account, projectRoles)
    ))


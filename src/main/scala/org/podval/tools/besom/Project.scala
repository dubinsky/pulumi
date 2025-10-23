package org.podval.tools.besom

import besom.{Context, Output}
import besom.api.gcp.organizations.{Project as ProjectGCP, ProjectArgs}
import besom.api.gcp.projects.{Service, ServiceArgs}

// pulumi import "gcp:organizations/project:Project" "project:<project id>" "projects/<project id>"
final class Project(
  id: String,
  displayName: String,
  services: Seq[String],
  labels: Map[String, String] = Map.empty,
  autoCreateNetwork: Boolean = true // TODO do I need the default network?
)(using ctx: Context, gcp: Gcp) extends WithResources:
  val project: Output[ProjectGCP] = ProjectGCP(name = s"project:$id", ProjectArgs(
    orgId = gcp.orgId,
    billingAccount = gcp.billingAccountId,
    projectId = id,
    name = displayName,
    labels = if labels.isEmpty then None else Some(labels),
    autoCreateNetwork = autoCreateNetwork
  ))

  override def resources: Seq[Output[?]] =
    // pulumi import "gcp:projects/service:Service" "project:<project id>/service:<service>" "<project id>/<service>.googleapis.com"
    val serviceResources: Seq[Output[Service]] =
      for service <- services yield
        for
          projectId: String <- project.projectId
          result: Service <- Service(s"project:$projectId/service:$service", ServiceArgs(
            project = projectId,
            service = s"$service.googleapis.com"
          ))
        yield result

    Seq(project) ++
    serviceResources

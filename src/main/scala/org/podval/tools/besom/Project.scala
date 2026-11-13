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
  // Games and Firebase pass false by name. The default stays true.
  autoCreateNetwork: Boolean = true
)(using ctx: Context, gcp: Gcp) extends WithResources:
  // Public. AlterRebbe reads projectId and number.
  val project: Output[ProjectGCP] = ProjectGCP(name = Names.projectPulumiName(id), ProjectArgs(
    orgId = gcp.orgId,
    billingAccount = gcp.billingAccountId,
    projectId = id,
    name = displayName,
    // Empty stays None. Firebase is the stack that passes labels (firebase -> enabled).
    labels = if labels.isEmpty then None else Some(labels),
    autoCreateNetwork = autoCreateNetwork
  ), gcpResourceOptions())

  override def resources: Seq[Output[?]] =
    // pulumi import "gcp:projects/service:Service" "project:<project id>/service:<service>" "<project id>/<service>.googleapis.com"
    val serviceResources: Seq[Output[Service]] =
      for service <- services yield
        for
          projectId: String <- project.projectId
          result: Service <- Service(Names.projectServiceName(projectId, service), ServiceArgs(
            project = projectId,
            service = s"$service.googleapis.com"
          ), gcpResourceOptions())
        yield result

    Seq(project) ++
    serviceResources

package org.podval.tools.besom

import besom.{Context, Output}
import org.podval.tools.besom.{Gcp, Project, ServiceAccount}

final class DevPod(projectId: String)(using ctx: Context, gcp: Gcp) extends WithResources:
  // podval.org:
  // It seems in July 2024 a VM was created with a 50GB Balanced persistent disk,
  // for which I have been paying $5/month even though the VM is stopped...
  // I do not remember how it was created - manually or by DevPod;
  // if I need it again, I probably should create it from here :)
  // Deleting the VM and the disk for now.
  override def resources: Seq[Output[?]] =
    val project: Project = Project(
      id = projectId,
      displayName = projectId,
      services = Seq("compute")
    )

    val devPodServiceAccount: ServiceAccount = ServiceAccount(
      project = project,
      id = "devpod",
      displayName = "Service account for running DevPod",
      description = "",
      organizationRoles = Seq.empty,
      projectRoles = Seq(
        "serviceusage.serviceUsageConsumer",
        "compute.instanceAdmin.v1", // Note: seems sufficient, no need to escalate to "compute.admin"
        "iam.serviceAccountUser"
      )
    )

    val devPodVMServiceAccount: ServiceAccount = ServiceAccount(
      project = project,
      id = "devpod-vm",
      displayName = "Service account for running DevPod Virtual Machines",
      description = "",
      organizationRoles = Seq.empty,
      projectRoles = Seq.empty
    )

    WithResources(Seq(
      project,
      devPodServiceAccount,
      devPodVMServiceAccount
    ))

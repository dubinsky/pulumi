package org.podval.tools.besom

// Google-managed service agent, service-<projectNumber>@gcp-sa-<service>.iam.gserviceaccount.com.
// Not a user-created Account, and not a compute-system agent (that email is a different shape).
// resourceName and member are the same string, including .iam.gserviceaccount.com, so ProjectIam's
// logical name project:$projectId/$member/role:$role matches AlterRebbe's direct IamMember.
// Pass the role unprefixed (storage.objectViewer). ProjectIam prepends roles/.
final case class ServiceAgent(projectNumber: String, service: String):
  def member: String = Names.serviceAgentMember(projectNumber, service)
  def resourceName: String = Names.serviceAgentResourceName(projectNumber, service)

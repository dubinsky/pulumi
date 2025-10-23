resource "googleworkspace_group" "gcp-organization-admins" {
  email       = "gcp-organization-admins@domain.tld"
  description = "Organization administrators have access to administer all resources belonging to the organization"
}

resource "google_organization_iam_member" "organization-admins" {
  org_id = data.google_organization.organization.org_id
  member = "group:${googleworkspace_group.gcp-organization-admins.email}"
  role   = "roles/${each.value}"
  for_each = toset([
    "billing.admin",
    "cloudasset.owner",
    "iam.organizationRoleViewer",
    "iam.serviceAccountAdmin",
    "iam.serviceAccountKeyAdmin",
    "oauthconfig.editor",
    "recommender.iamViewer",
    "recommender.projectUtilAdmin",
    "resourcemanager.organizationAdmin",
    "resourcemanager.projectCreator",
    "resourcemanager.projectDeleter",
    "resourcemanager.projectIamAdmin",
    "resourcemanager.projectUndeleter",
    "resourcemanager.projectMover",
    "serviceusage.apiKeysAdmin",
    "serviceusage.serviceUsageAdmin",
    "storage.admin"
  ])
}

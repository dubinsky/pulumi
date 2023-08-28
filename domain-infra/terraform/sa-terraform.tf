resource "google_service_account" "terraform" {
  project      = google_project.infra.project_id
  account_id   = "terraform"
  display_name = "terraform"
  description  = "Service Account for Terraform"
}

resource "google_organization_iam_member" "terraform" {
  org_id = data.google_organization.organization.org_id
  member = "serviceAccount:${google_service_account.terraform.email}"
  role   = "roles/${each.value}"
  for_each = toset([
    "billing.admin",
    "compute.admin",
    "dns.admin",
    "domains.admin",
    "iam.organizationRoleAdmin",
    "iam.serviceAccountAdmin",
    "managedidentities.admin",
    "resourcemanager.organizationAdmin",
    "resourcemanager.folderAdmin",
    "resourcemanager.projectCreator",
    "serviceusage.serviceUsageAdmin",
    "storage.admin"
  ])
}

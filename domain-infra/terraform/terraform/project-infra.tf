resource "google_project" "infra" {
  name                = "Domain Cloud Infrastructure"
  project_id          = "domain-infra"
  org_id              = data.google_organization.organization.org_id
  billing_account     = data.google_billing_account.account.id
  auto_create_network = true
  skip_delete         = true
  timeouts {}
}

resource "google_project_service" "infra" {
  project            = google_project.infra.project_id
  disable_on_destroy = true
  service            = "${each.value}.googleapis.com"
  for_each = toset([
    # if using Google Workspace provider:
    #    "admin", # "Admin SDK API" for user/group operations
    "cloudbilling", # "Cloud Billing API"
    "cloudidentity",
    "cloudresourcemanager", # "Cloud Resource Manager API" for project operations
    # if using Google Workspace provider:
    #    "groupssettings", # "Groups Settings API"
    "iam", # "Identity and Access Management (IAM) API" for Service Account creation
    "iamcredentials", # "IAM Service Account Credentials API"
    "logging",
    "monitoring",
    "serviceusage", # "Service Usage API" for listing/enabling/disabling services
    "storage",
    "storage-api",
    "storage-component",
  ])
}

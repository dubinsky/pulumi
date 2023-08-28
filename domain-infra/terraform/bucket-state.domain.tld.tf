resource "google_storage_bucket" "state" {
  project                     = google_project.infra.project_id
  force_destroy               = true
  location                    = local.gcp_region
  name                        = "state.domain.tld"
  storage_class               = "STANDARD"
  uniform_bucket_level_access = true
}

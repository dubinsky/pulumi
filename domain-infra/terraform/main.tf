locals {
  gcp_region = "us-east1"
}

terraform {
  required_version = ">= 0.14"

  required_providers {
    google = {
      source  = "hashicorp/google"
      version = ">=4.35.0"
    }

    # if using Google Workspace provider:
    #googleworkspace = {
    #  source  = "hashicorp/googleworkspace"
    #  version = ">=0.7.0"
    #}
  }

  # This stays commented out until the state is migrated into the bucket:
  #  backend "gcs" {
  #    bucket = "state.domain.tld" # locals are not allowed here
  #    prefix = "terraform"
  #  }
}

provider "google" {
  region = local.gcp_region
}

data "google_organization" "organization" {
  domain = "domain.tld"
}

data "google_billing_account" "account" {
  display_name    = "My Billing Account"
  open            = true
}

# if using Google Workspace provider:
#provider "googleworkspace" {
#  customer_id = data.google_organization.organization.directory_customer_id
#  oauth_scopes = [
#    "https://www.googleapis.com/auth/admin.directory.user",
#    "https://www.googleapis.com/auth/admin.directory.userschema",
#    "https://www.googleapis.com/auth/admin.directory.group",
#    "https://www.googleapis.com/auth/apps.groups.settings",
#    "https://www.googleapis.com/auth/admin.directory.domain",
#    "https://www.googleapis.com/auth/admin.directory.rolemanagement",
#    "https://www.googleapis.com/auth/gmail.settings.basic",
#    "https://www.googleapis.com/auth/gmail.settings.sharing",
#    "https://www.googleapis.com/auth/chrome.management.policy",
#    "https://www.googleapis.com/auth/cloud-platform",
#    "https://www.googleapis.com/auth/admin.directory.customer",
#    "https://www.googleapis.com/auth/admin.directory.orgunit",
#    "https://www.googleapis.com/auth/userinfo.email",
#    "https://www.googleapis.com/auth/cloud-identity.groups",
#  ]
#}

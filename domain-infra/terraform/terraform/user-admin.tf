# if using Google Workspace provider to manage users:
#resource "googleworkspace_user" "admin" {
#  primary_email  = "admin@domain.tld"
#  timeouts {}
#}

resource "googleworkspace_group_member" "dub-gcp-organization-admins" {
  group_id = googleworkspace_group.gcp-organization-admins.id
  email    = "admin@domain.tld"
  # if using Google Workspace provider to manage users:
  # email    = googleworkspace_user.dub.primary_email
  role     = "OWNER"
}

package org.podval.tools.pulumi

import com.pulumi.core.Output
import com.pulumi.gcp.cloudidentity.Group
import com.pulumi.gcp.serviceaccount.Account
import com.pulumi.resources.Resource

sealed trait Principal[A]:
  extension(a: A)
    def principalResourceName: String
    def principalMemberOutput: Output[String] = Output.of(principalResourceName)

object Principal:
  object AllUsers

  given Principal[AllUsers.type] with
    extension(allUsers: AllUsers.type)
      override def principalResourceName: String = "allUsers"

  given Principal[User] with
    extension(user: User)
      override def principalResourceName: String = user.resourceName

  sealed trait ResourcePrincipal[A <: Resource] extends Principal[A]:
    extension (resource: A)
      override def principalResourceName: String = resource.pulumiResourceName

  given ResourcePrincipal[Group] = new ResourcePrincipal[Group]{}

  given ResourcePrincipal[Account] with
    extension(serviceAccount: Account)
      override def principalMemberOutput: Output[String] = serviceAccount.member

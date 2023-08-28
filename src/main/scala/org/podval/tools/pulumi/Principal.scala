package org.podval.tools.pulumi

import com.pulumi.core.Output
import com.pulumi.gcp.cloudidentity.Group
import com.pulumi.gcp.serviceAccount.Account

trait Principal[A]:
  extension(a: A)
    def principalResourceName: String
    def principalMemberOutput: Output[String] = Output.of(principalResourceName)

object Principal:
  object AllUsers

  given Principal[AllUsers.type] with
    extension (allUsers: AllUsers.type)
      override def principalResourceName: String = "allUsers"

  given Principal[User] with
    extension(user: User)
      override def principalResourceName: String = user.resourceName

  given Principal[Group] with
    extension(group: Group)
      override def principalResourceName: String = group.pulumiResourceName

  given Principal[Account] with
    extension(serviceAccount: Account)
      override def principalResourceName: String = serviceAccount.pulumiResourceName
      override def principalMemberOutput: Output[String] = serviceAccount.member

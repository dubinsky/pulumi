package org.podval.tools.besom

import besom.{Context, Output}
import besom.internal.Resource
import besom.api.gcp.cloudidentity.Group
import besom.api.gcp.serviceaccount.Account

sealed trait Principal[A]:
  extension(a: Output[A])(using ctx: Context)
    def principalResourceName: Output[String]
    def principalMember: Output[String] = principalResourceName

// TODO add all allowed Principal kinds
object Principal:
  // A special identifier that represents anyone who is on the internet; with or without a Google account.
  object AllUsers

  given Principal[AllUsers.type]:
    extension(allUsers: Output[AllUsers.type])(using ctx: Context)
      override def principalResourceName: Output[String] = Output("allUsers")

  // A special identifier that represents anyone who is authenticated with a Google account or a service account.
  object AllAuthenticatedUsers

  given Principal[AllAuthenticatedUsers.type]:
    extension (allUsers: Output[AllAuthenticatedUsers.type])(using ctx: Context)
      override def principalResourceName: Output[String] = Output("allAuthenticatedUsers")
      
  // user:{emailid}: An email address that represents a specific Google account.    
  given Principal[User]:
    extension (user: Output[User])(using ctx: Context)
      override def principalResourceName: Output[String] = user.map(_.resourceName)

  sealed trait ResourcePrincipal[A <: Resource] extends Principal[A]:
    extension (resource: Output[A])(using ctx: Context)
      override def principalResourceName: Output[String] = resource.flatMap(_.pulumiResourceName)

  // group:{emailid}: An email address that represents a Google group.
  given ResourcePrincipal[Group] = new ResourcePrincipal[Group]{}

  // serviceAccount:{emailid}: An email address that represents a service account
  given ResourcePrincipal[Account]:
    extension(serviceAccount: Output[Account])(using ctx: Context)
      override def principalMember: Output[String] = serviceAccount.member

  // TODO
  //domain:{domain}: A G Suite domain (primary, instead of alias) name that represents all the users of that domain. For example, google.com or example.com.
  //projectOwner:projectid: Owners of the given project. For example, "projectOwner:my-example-project"
  //projectEditor:projectid: Editors of the given project. For example, "projectEditor:my-example-project"
  //projectViewer:projectid: Viewers of the given project. For example, "projectViewer:my-example-project"
package org.podval.tools.besom

import besom.{Context, Output}
import besom.internal.Resource
import besom.api.gcp.cloudidentity.Group
import besom.api.gcp.serviceAccount.Account

sealed trait Principal[A]:
  extension(a: Output[A])(using ctx: Context)
    def principalResourceName: Output[String]
    def principalMember: Output[String] = principalResourceName

// TODO add all allowed Principal kinds
object Principal:
  object AllUsers

  given Principal[AllUsers.type] with
    extension(allUsers: Output[AllUsers.type])(using ctx: Context)
      override def principalResourceName: Output[String] = Output("allUsers")

  given Principal[User] with
    extension (user: Output[User])(using ctx: Context)
      override def principalResourceName: Output[String] = user.map(_.resourceName)

  sealed trait ResourcePrincipal[A <: Resource] extends Principal[A]:
    extension (resource: Output[A])(using ctx: Context)
      override def principalResourceName: Output[String] = resource.flatMap(_.urn).map(_.resourceName)

  given ResourcePrincipal[Group] = new ResourcePrincipal[Group]{}

  given ResourcePrincipal[Account] with
    extension(serviceAccount: Output[Account])(using ctx: Context)
      override def principalMember: Output[String] = serviceAccount.member

package org.podval.tools.besom

import besom.Output

final class User(val name: String, val domain: String):
  def email: String = Names.userEmail(name, domain)
  def resourceName: String = Names.userResourceName(name, domain) // TODO unfold
  
object User:
  def gmail(name: String): User = User(name, "gmail.com")

  def user(name: String)(using gcp: Gcp): User = User(name, gcp.domain)

  def userOutput(name: String)(using gcp: Gcp): Output[User] = Output(User(name, gcp.domain))


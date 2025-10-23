package org.podval.tools.besom

import besom.Output

final class User(val name: String, val domain: String):
  def email: String = s"$name@$domain"
  def resourceName: String = s"user:$email"// TODO unfold
  
object User:
  def gmail(name: String): User = User(name, "gmail.com")

  def user(name: String)(using gcp: Gcp): User = User(name, gcp.domain)

  def userOutput(name: String)(using gcp: Gcp): Output[User] = Output(User(name, gcp.domain))


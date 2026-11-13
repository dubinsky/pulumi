package org.podval.tools.besom

import besom.Output

final class User(val name: String, val domain: String):
  def email: String = Names.userEmail(name, domain)
  def resourceName: String = Names.userResourceName(name, domain)
  
object User:
  // No caller. Stays: a Gmail principal is still a User, just not one any stack builds.
  def gmail(name: String): User = User(name, "gmail.com")

  // All three programs call this. The shared call site is administrators.
  // CognoMath also passes it as group owners and members. Returns User.
  def user(name: String)(using gcp: Gcp): User = User(name, gcp.domain)

  // ProjectIam needs Output[A]. Firebase calls this for nina.dubinsky and eric.karnowski.
  def userOutput(name: String)(using gcp: Gcp): Output[User] = Output(User(name, gcp.domain))


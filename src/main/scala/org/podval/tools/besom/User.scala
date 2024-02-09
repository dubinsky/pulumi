package org.podval.tools.besom

final class User(val name: String, val domain: String):
  def email: String = s"$name@$domain"
  def resourceName: String = s"user:$email"
  
object User:
  def gmail(name: String): User = User(name, "gmail.com")

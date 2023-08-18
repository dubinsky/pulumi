package org.podval.tools.pulumi

final class User(val name: String, val domain: String):
  def email: String = s"$name@$domain"
  def resourceName: String = s"user:$email"


package org.podval.tools.besom

import org.scalatest.funsuite.AnyFunSuite

final class ServiceAccountKeyTest extends AnyFunSuite:
  test("guest hostname trims a trailing newline, and another host does not match"):
    assert(ServiceAccountKey.isCloudInfrastructureGuest("cloud-infrastructure\n") == true)
    assert(ServiceAccountKey.isCloudInfrastructureGuest("cloud-infrastructure \n") == true)
    assert(ServiceAccountKey.isCloudInfrastructureGuest("cloud-infrastructure") == true)
    assert(ServiceAccountKey.isCloudInfrastructureGuest("localhost") == false)
    assert(ServiceAccountKey.isCloudInfrastructureGuest("localhost\n") == false)
    assert(ServiceAccountKey.isCloudInfrastructureGuest("not-cloud-infrastructure") == false)

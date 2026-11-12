package org.podval.tools.besom

import besom.Output

// Stack forces every Output in this Seq, including a resource built inside flatMap.
// The type stays Seq[Output[?]] because AlterRebbe appends a raw Output.
// A ComponentResource parent would change every child URN.
trait WithResources:
  def resources: Seq[Output[?]]

object WithResources:
  // Not idempotent. Main calls this once, after groups() and dnsZones() have already run.
  def apply(components: Seq[WithResources]): Seq[Output[?]] = components.flatMap(_.resources)

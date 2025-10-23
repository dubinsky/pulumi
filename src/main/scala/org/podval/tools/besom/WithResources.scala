package org.podval.tools.besom

import besom.Output

trait WithResources:
  def resources: Seq[Output[?]]

object WithResources:
  def apply(components: Seq[WithResources]): Seq[Output[?]] = components.flatMap(_.resources)

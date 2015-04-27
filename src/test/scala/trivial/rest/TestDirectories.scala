package trivial.rest

import java.util.concurrent.atomic.AtomicInteger

import scala.reflect.io.Directory

object TestDirectories {
  private val base = System.currentTimeMillis
  private val suffix = new AtomicInteger(1)
  def nextTestDir = Directory(s"target/jofs/${base}-${suffix.incrementAndGet()}")
}
package trivial.rest

import java.util.concurrent.atomic.AtomicInteger

import trivial.rest.persistence.FileSystem

import scala.reflect.io.{File, Directory}

object TestDirectories {
  private val base = System.currentTimeMillis
  private val suffix = new AtomicInteger(1)
  def nextTestDirPath = s"target/jofs/${base}-${suffix.incrementAndGet()}"
  def nextTestDir = Directory(nextTestDirPath).createDirectory()
  def cleanTestDirs() = Directory(s"target/jofs").deleteRecursively()
  def provisionedTestDir = {
    val docRoot = Directory(nextTestDir)
    docRoot.createDirectory()
    val resourcesDir = Directory("src/test/resources")
    FileSystem.copy(File(resourcesDir / "currency.json"), File(docRoot / "currency.json"))
    FileSystem.copy(File(resourcesDir / "exchangerate.json"), File(docRoot / "exchangerate.json"))
    FileSystem.save("100", File(docRoot / "_sequence.json"))
    docRoot
  }
}
package trivial.rest.persistence

import java.io.File

import org.apache.commons.io.FileUtils._

class FileSystem(docRoot: String) extends Persister {
  override def loadAll(resourceName: String): Array[Byte] = readFileToByteArray(fileFor(resourceName))

  def fileFor(resourceName: String): File = new File(docRoot, s"$resourceName.json")
}
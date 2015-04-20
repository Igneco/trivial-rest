package trivial.rest.persistence

import java.io.File

import org.apache.commons.io.FileUtils._

class JsonOnFileSystem(docRoot: String) extends Persister {
  override def loadAll(resourceName: String) = {
    if (hasLocalFile(fileFor(resourceName)))
      Right(readFileToByteArray(fileFor(resourceName)))
    else
      Left(s"File not found: ${fileFor(resourceName).getAbsolutePath}")
  }

  def fileFor(resourceName: String): File = new File(docRoot, s"$resourceName.json")

  def hasLocalFile(file: File): Boolean = {
    if(file.toString.contains(".."))     return false
    if(!file.exists || file.isDirectory) return false
    if(!file.canRead)                    return false

    true
  }
}
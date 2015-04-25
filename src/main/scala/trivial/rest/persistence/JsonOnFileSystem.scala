package trivial.rest.persistence

import org.apache.commons.io.FileUtils._

import scala.reflect.io.{File, Directory}

class JsonOnFileSystem(docRoot: Directory) extends Persister {

  override def loadAll(resourceName: String) = {
    if (hasLocalFile(fileFor(resourceName)))
      Right(readFileToByteArray(fileFor(resourceName).jfile))
    else
      Left(s"File not found: ${fileFor(resourceName).toAbsolute}")
  }

  override def save(resourceName: String, content: String): Either[String, Array[Byte]] = {
    if (docRoot.notExists) docRoot.createDirectory()
    val targetFile = fileFor(resourceName)
    if (targetFile.notExists) {
      targetFile.createFile()
      targetFile.appendAll(content)
    } else {
      targetFile.appendAll(",\n", content)
    }
    Right(content.getBytes)
  }

  // TODO - CAS - 21/04/15 - Consider Scala async to make this write-behind: https://github.com/scala/async
  // TODO - CAS - 21/04/15 - Make this less ugly
  override def nextSequenceNumber: Int = {
    val resourceName = "_sequence"
    if (docRoot.notExists) docRoot.createDirectory()
    val targetFile = fileFor(resourceName)
    if (targetFile.notExists) {
      targetFile.createFile()
      targetFile.appendAll("1")
      1
    } else {
      val previous = targetFile.slurp().toInt
      val next = previous + 1
      targetFile.writeAll(next.toString)
      next
    }
  }

  def fileFor(resourceName: String): File = File(docRoot / s"$resourceName.json")

  def hasLocalFile(file: File): Boolean = {
    if(file.toString.contains(".."))     return false
    if(!file.exists || file.isDirectory) return false
    if(!file.canRead)                    return false

    true
  }
}
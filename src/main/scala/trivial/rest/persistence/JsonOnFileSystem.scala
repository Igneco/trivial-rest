package trivial.rest.persistence

import org.apache.commons.io.FileUtils._
import org.json4s.Formats
import org.json4s.native.Serialization
import trivial.rest.caching.Memo
import trivial.rest.serialisation.Serialiser
import trivial.rest.{Failure, Resource}

import scala.reflect.io.{Directory, File}

class JsonOnFileSystem(docRoot: Directory, serialiser: Serialiser) extends Persister with Memo {

  override def loadAll[T <: Resource[T] : Manifest](resourceName: String)(implicit formats: Formats): Either[Failure, Seq[T]] =
    memo(resourceName) { actuallyLoadAll[T] }(resourceName)

  def actuallyLoadAll[T <: Resource[T] with AnyRef : Manifest](resourceName: String)(implicit formats: Formats): Either[Failure, Seq[T]] = {
    if (hasLocalFile(fileFor(resourceName)))
      serialiser.deserialise(fromDisk(resourceName))
    else
      Right(Seq.empty)
  }

  override def save[T <: Resource[T] : Manifest](resourceName: String, newItems: Seq[T])(implicit formats: Formats): Either[Failure, Int] = {
    unMemo(resourceName)

    loadAll[T](resourceName).right.map { previousItems =>
      assuredFile(docRoot, resourceName).writeAll(Serialization.write(previousItems ++ newItems))
    }

    Right(newItems.size)
  }

  // TODO - CAS - 21/04/15 - Consider Scala async to make this write-behind: https://github.com/scala/async
  // TODO - CAS - 21/04/15 - Make this less ugly
  override def nextSequenceNumber: Int = {
    val targetFile = assuredFile(docRoot, "_sequence", "0")
    val previous = targetFile.slurp().toInt
    val next = previous + 1
    targetFile.writeAll(next.toString)
    next
  }

  // TODO - CAS - 14/05/15 - Extract FS methods to a separate FileSystem dependency?

  def assuredFile(docRoot: Directory, targetResourceName: String, defaultContents: String = ""): File = {
    if (docRoot.notExists) docRoot.createDirectory()
    val targetFile = fileFor(targetResourceName)
    if (targetFile.notExists) {
      targetFile.createFile()
      targetFile.writeAll(defaultContents)
    }
    targetFile
  }

  private def fromDisk[T <: Resource[T]](resourceName: String): String =
    readFileToString(fileFor(resourceName).jfile)

  def fileFor(resourceName: String): File = File(docRoot / s"$resourceName.json")

  def hasLocalFile(file: File): Boolean = {
    if(file.toString().contains(".."))   return false
    if(!file.exists || file.isDirectory) return false
    if(!file.canRead)                    return false

    true
  }
}
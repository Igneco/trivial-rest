package trivial.rest.persistence

import org.apache.commons.io.FileUtils._
import org.json4s.native.Serialization
import org.json4s.{Formats, MappingException}
import trivial.rest.serialisation.{Serialiser, SerialiserExceptionHelper}
import trivial.rest.{Failure, Resource}

import scala.reflect.io.{Directory, File}

class JsonOnFileSystem(docRoot: Directory, serialiser: Serialiser) extends Persister {

  override def loadAll[T <: Resource[T] with AnyRef : Manifest](resourceName: String)(implicit formats: Formats): Either[Failure, Seq[T]] = {
    if (hasLocalFile(fileFor(resourceName)))
      serialiser.deserialise(fromDisk(resourceName))
    else
      Right(Seq.empty)
  }

  // TODO - CAS - 01/05/15 - Require a ClassTag, so that we can fail if no class is specified, or tell the client what the class was that didn't load
  override def save[T <: Resource[T] : Manifest](resourceName: String, newItems: Seq[T])(implicit formats: Formats): Either[Failure, Int] = {
    if (docRoot.notExists) docRoot.createDirectory()
    val targetFile = fileFor(resourceName)
    if (targetFile.notExists) targetFile.createFile()

    // TODO - CAS - 06/05/15 - Invalidate the cache of T

    loadAll[T](resourceName).right.map { previousItems =>
      targetFile.writeAll(Serialization.write(previousItems ++ newItems))
    }

    Right(newItems.size)
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

  // TODO - CAS - 14/05/15 - Extract to a separate FileSystem dependency?

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
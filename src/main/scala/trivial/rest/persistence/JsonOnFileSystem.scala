package trivial.rest.persistence

import org.apache.commons.io.FileUtils._
import org.json4s.native.Serialization
import org.json4s.{Formats, MappingException}
import trivial.rest.serialisation.SerialiserExceptionHelper
import trivial.rest.{Failure, Resource}

import scala.reflect.io.{Directory, File}

class JsonOnFileSystem(docRoot: Directory) extends Persister {

  override def loadAll[T <: Resource[T] with AnyRef : Manifest](resourceName: String)(implicit formats: Formats): Either[Failure, Seq[T]] = {
    def deserialise(body: String): Either[Failure, Seq[T]] =
      try {
        // TODO - CAS - 07/05/15 - If T is Nothing, then someone hasn't specified a type parameter somewhere. Try implicitly[Manifest[T]].toString
        Right(Serialization.read[Seq[T]](body))
      } catch {
        /* TODO - CAS - 01/05/15 - Map these to better error messages

        (1)
        // The extract[] method doesn't know the type of T, probably because it can't infer it.
        // Pass in the type explicitly. This is trying to create a Seq[Nothing], so it is trying
        // to build Nothings and failing. Fixed by calling loadAll[T](param) instead of loadAll(param)

        two: Left(Failure(500,Failed to deserialise into foo, due to: org.json4s.package$MappingException: Parsed JSON values do not match with class constructor
        args=
        arg types=
        constructor=public scala.runtime.Nothing$()))

        (2)
        // We have pulled an ID off disk, and we don't know how to map it to a thing.

        Left(Failure(500,Failed to deserialise into exchangerate, due to: org.json4s.package$MappingException: No usable value for currency
        No usable value for isoName
        Did not find value which can be converted into java.lang.String

        */
        // TODO - CAS - 01/05/15 - Try parsing the JSON AST, and showing that, for MappingException, which is about converting AST -> T
        case m: MappingException => Left(Failure(500, SerialiserExceptionHelper.huntCause(m, Seq.empty[String])))
        case e: Exception => Left(Failure(500, s"THE ONE IN JsonOnFileSystem ===> Failed to deserialise into $resourceName, due to: $e"))
      }

    if (hasLocalFile(fileFor(resourceName)))
      deserialise(fromDisk(resourceName))
    else
      Right(Seq.empty)
  }

  private def fromDisk[T <: Resource[T]](resourceName: String): String =
    readFileToString(fileFor(resourceName).jfile)

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

  def fileFor(resourceName: String): File = File(docRoot / s"$resourceName.json")

  def hasLocalFile(file: File): Boolean = {
    if(file.toString().contains(".."))   return false
    if(!file.exists || file.isDirectory) return false
    if(!file.canRead)                    return false

    true
  }
}
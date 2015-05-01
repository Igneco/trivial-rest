package trivial.rest.persistence

import org.apache.commons.io.FileUtils._
import org.json4s.{JValue, NoTypeHints, Formats}
import org.json4s.native.{JsonParser, Serialization}
import trivial.rest.{Restable, Failure}

import scala.reflect.ClassTag
import scala.reflect.io.{File, Directory}

class JsonOnFileSystem(docRoot: Directory) extends Persister {

  implicit val formats: Formats = Serialization.formats(NoTypeHints)

  override def loadAll[T <: Restable[T] with AnyRef](resourceName: String)(implicit mf: scala.reflect.Manifest[T]): Either[Failure, Seq[T]] = {
    def deserialise(body: String): Either[Failure, Seq[T]] =
      try {
        Right(Serialization.read[Seq[T]](body))
      } catch {
        /* TODO - CAS - 01/05/15 - Map these to a better error message
        // The extract[] method doesn't know the type of T, probably because it can't infer it.
        // Pass in the type explicitly. This is trying to create a Seq[Nothing], so it is trying
        // to build Nothings and failing. Fixed by calling loadAll[T](param) instead of loadAll(param)

        two: Left(Failure(500,Failed to deserialise into foo, due to: org.json4s.package$MappingException: Parsed JSON values do not match with class constructor
        args=
        arg types=
        constructor=public scala.runtime.Nothing$()))
        */
        // TODO - CAS - 01/05/15 - Try parsing the JSON AST, and showing that, for MappingException, which is about converting AST -> T
        case e: Exception => Left(Failure(500, s"Failed to deserialise into $resourceName, due to: $e"))
      }
    
    if (hasLocalFile(fileFor(resourceName)))
      deserialise(fromDisk(resourceName))
    else
      Right(Seq.empty)
  }

  private def fromDisk[T <: Restable[T]](resourceName: String): String =
    readFileToString(fileFor(resourceName).jfile)

  // TODO - CAS - 01/05/15 - Require a ClassTag, so that we can fail if no class is specfied, or tell the client what the class was that didn't load
  override def save[T <: Restable[T]](resourceName: String, t: Seq[T])(implicit mf: scala.reflect.Manifest[T]): Either[Failure, Seq[T]] = {
    if (docRoot.notExists) docRoot.createDirectory()
    val targetFile = fileFor(resourceName)
    if (targetFile.notExists) targetFile.createFile()

    loadAll[T](resourceName).right.map{previous =>
      val newAll: Seq[T] = previous ++ t
      targetFile.writeAll(Serialization.write(newAll))
      newAll
    }
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
    if(file.toString().contains(".."))     return false
    if(!file.exists || file.isDirectory) return false
    if(!file.canRead)                    return false

    true
  }
}
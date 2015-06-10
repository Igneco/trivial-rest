package trivial.rest.persistence

import org.apache.commons.io.FileUtils._
import org.joda.time.format.{DateTimeFormat, DateTimeFormatter}
import org.joda.time.{DateTime, DateTimeZone}
import org.json4s.native.Serialization
import trivial.rest.caching.Memo
import trivial.rest.serialisation.Serialiser
import trivial.rest.{Classy, Failure, Resource}

import scala.reflect.ClassTag
import scala.reflect.io.{Directory, File}

class JsonOnFileSystem(docRoot: Directory, serialiser: Serialiser) extends Persister with Memo {

  override def loadAll[T <: Resource[T] : Manifest](resourceName: String): Either[Failure, Seq[T]] =
    memo(resourceName) { actuallyLoadAll[T] }(resourceName)

  private def actuallyLoadAll[T <: Resource[T] with AnyRef : Manifest](resourceName: String): Either[Failure, Seq[T]] =
    if (hasLocalFile(fileFor(resourceName)))
      serialiser.deserialise(fromDisk(resourceName))
    else
      Right(Seq.empty)

  override def migrate[T <: Resource[T] : ClassTag : Manifest](forward: (T) => T, oldResourceName: Option[String]) = {
    // TODO - CAS - 09/06/15 - Make Classy work with Manifests as well as ClassTags.
    val targetName = Classy.name[T].toLowerCase
    val sourceName = oldResourceName.getOrElse(targetName)
    val backupName = s"$sourceName-${stamp()}"

    FileSystem.move(assuredFile(docRoot, sourceName), fileFor(backupName))

    val oldData: Either[Failure, Seq[T]] = loadAll[T](backupName)
    println(s"oldData: ${oldData}")
    val migratedData: Either[Failure, Seq[T]] = oldData.right.map(_.map(forward))
    println(s"migratedData: ${migratedData}")
    val saved: Either[Failure, Int] = migratedData.right.flatMap(seqTs => save(targetName, seqTs))
    println(s"saved: ${saved}")
  }

  private lazy val timestampFormat: DateTimeFormatter = DateTimeFormat.forPattern("yyyyddMMHHmmssSSS")
  private def stamp(): String = DateTime.now(DateTimeZone.UTC).toString(timestampFormat)

  override def save[T <: Resource[T] : Manifest](resourceName: String, newItems: Seq[T]): Either[Failure, Int] =
    loadAll[T](resourceName).right.map { previousItems =>
      // TODO - CAS - 09/06/15 - Change this to call serialiser.serialise(previousItems ++ newItems)
      assuredFile(docRoot, resourceName).writeAll(Serialization.write(previousItems ++ newItems)(serialiser.formatsExcept[T]))
      unMemo(resourceName)
      newItems.size
    }

  // TODO - CAS - 21/04/15 - Consider Scala async to make this write-behind: https://github.com/scala/async
  override def nextSequenceId: String = {
    val targetFile = assuredFile(docRoot, "_sequence", "0")
    val previous = targetFile.slurp().toInt
    val next = previous + 1
    targetFile.writeAll(s"$next")
    formatSequenceId(next)
  }

  override def formatSequenceId(id: Int): String = f"$id%07d"

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
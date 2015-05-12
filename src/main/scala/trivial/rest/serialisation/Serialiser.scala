package trivial.rest.serialisation

import org.json4s.{NoTypeHints, MappingException, Formats}
import org.json4s.native.Serialization
import trivial.rest.{Classy, Failure, Resource}

import scala.collection.mutable
import scala.reflect.ClassTag

/**
 *
 * Common errors:
 *
 * Problem: we expect a runtime type of T, but it is instead scala.runtime.Nothing$
 * Cause: an implicit call to formatsExcept[T : ClassTag] is not filling in the type parameter. Consider
 *   (a) calling formatsExcept[T : ClassTag] explicitly, or
 *   (b) bringing it in scope with implicit val formats = formatsExcept[T]
 */
trait Serialiser {
  def registerResource[T <: Resource[T] : ClassTag](allTheTs: Formats => Either[Failure, Seq[T]]): Unit
  implicit def formatsExcept[T : ClassTag]: Formats
  def deserialise[T <: Resource[T] : Manifest](body: String): Either[Failure, Seq[T]]
  def serialise[T <: Resource[T] : ClassTag](seqTs: Seq[T]): String = Serialization.write(seqTs)(formatsExcept[T])
}

class Json4sSerialiser extends Serialiser {

  private val resourceSerialisers = mutable.Map.empty[Class[_], ResourceSerialiser[_]]

  override def registerResource[T <: Resource[T] : ClassTag](allTheTs: Formats => Either[Failure, Seq[T]]) = {
    val serialiser = ResourceSerialiser[T](_.id.getOrElse(""), id => hunt(allTheTs(formatsExcept[T]), id))
    resourceSerialisers += Classy.runtimeClass[T] -> serialiser
  }

  // TODO - CAS - 11/05/15 - memoize
  override implicit def formatsExcept[T : ClassTag]: Formats =
    Serialization.formats(NoTypeHints) ++ (resourceSerialisers - Classy.runtimeClass[T]).values

  // TODO - CAS - 07/05/15 - Switch this to persister.getById, once we have /get/:id enabled
  def hunt[T <: Resource[T]](allTheTs: => Either[Failure, Seq[T]], id: String): Option[T] = {
    allTheTs match {
      case Right(seqTs) => seqTs.find(_.id == Some(id))
      case Left(failure) => None
    }
  }

  override def deserialise[T <: Resource[T] : Manifest](body: String) = {
    try {
      Right(Serialization.read[Seq[T]](body))
    } catch {
      case m: MappingException => Left(Failure(500, SerialiserExceptionHelper.huntCause(m, Seq.empty[String])))
      case e: Exception => Left(Failure(500, s"THE ONE IN SERIALISER ===> Failed to deserialise into [T], due to: $e"))
    }
  }
}
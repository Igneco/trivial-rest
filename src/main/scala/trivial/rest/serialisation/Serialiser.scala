package trivial.rest.serialisation

import org.json4s.Formats
import org.json4s.native.Serialization
import trivial.rest.{Failure, Resource}

import scala.reflect.ClassTag

/**
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


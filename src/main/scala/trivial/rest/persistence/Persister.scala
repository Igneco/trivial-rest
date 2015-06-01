package trivial.rest.persistence

import org.json4s.Formats
import trivial.rest.{Resource, Failure}

trait Persister {
  def save[T <: Resource[T] : Manifest](resourceName: String, content: Seq[T])(implicit formats: Formats): Either[Failure, Int]
  def loadAll[T <: Resource[T] : Manifest](resourceName: String)(implicit formats: Formats): Either[Failure, Seq[T]]
  def nextSequenceId: String
  def formatSequenceId(id: Int): String
}
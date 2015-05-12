package trivial.rest.persistence

import org.json4s.Formats
import trivial.rest.{Resource, Failure}

trait Persister {



  // TODO - CAS - 01/05/15 - URGENT - Require ClassTag[T], to force client to use a type parameter (also removes the need for the resource name param)



  // TODO - CAS - 27/04/15 - Expand to allow saving T, or JSON AST, or both. Overload save?
  def save[T <: Resource[T] : Manifest](resourceName: String, content: Seq[T])(implicit formats: Formats): Either[Failure, Int]
  def loadAll[T <: Resource[T] : Manifest](resourceName: String)(implicit formats: Formats): Either[Failure, Seq[T]]
  def nextSequenceNumber: Int
}
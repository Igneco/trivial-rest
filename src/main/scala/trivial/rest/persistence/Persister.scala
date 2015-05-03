package trivial.rest.persistence

import trivial.rest.{Restable, Failure}

trait Persister {
  
  
  
  // TODO - CAS - 01/05/15 - URGENT - Require ClassTag[T], to force client to use a type parameter (also removes the need for the resource name param)
  
  
  
  // TODO - CAS - 27/04/15 - Expand to allow saving T, or JSON AST, or both. Overload save?
  def save[T <: Restable[T]](resourceName: String, content: Seq[T])(implicit mf: scala.reflect.Manifest[T]): Either[Failure, Seq[T]]
  def loadAll[T <: Restable[T] : Manifest](resourceName: String): Either[Failure, Seq[T]]
  def nextSequenceNumber: Int
} 
package trivial.rest.persistence

import trivial.rest.Failure

trait Persister {
  // TODO - CAS - 27/04/15 - Expand to allow saving T, or JSON AST, or both. Overload save?
  def save(resourceName: String, content: String): Either[Failure, Array[Byte]]
  def loadAll(resourceName: String): Either[Failure, Array[Byte]]
  def nextSequenceNumber: Int
} 
package trivial.rest.persistence

import trivial.rest.Failure

trait Persister {
  def save(resourceName: String, content: String): Either[Failure, Array[Byte]]
  def loadAll(resourceName: String): Either[Failure, Array[Byte]]
  def nextSequenceNumber: Int
} 
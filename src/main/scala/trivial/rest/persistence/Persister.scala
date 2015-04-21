package trivial.rest.persistence

trait Persister {
  def save(resourceName: String, content: String): Either[String, Array[Byte]]
  def loadAll(resourceName: String): Either[String, Array[Byte]]
  def nextSequenceNumber: Int
} 
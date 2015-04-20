package trivial.rest.persistence

trait Persister {
  def loadAll(resourceName: String): Either[String, Array[Byte]]
} 
package trivial.rest.persistence

trait Persister {
  def loadAll(resourceName: String): Array[Byte]
} 
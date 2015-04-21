package trivial.rest.persistence

class PersisterContract(persister: Persister) {
  def testAll = {
    loadAll
    save
  }

  def loadAll = ???
  def save = ???
  def sequence = ???
}
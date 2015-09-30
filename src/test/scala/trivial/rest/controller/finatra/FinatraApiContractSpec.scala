package trivial.rest.controller.finatra

import trivial.rest.RestfulApiContract

class FinatraApiContractSpec extends RestfulApiContract {
  override val server = new FinatraApiTestWrapper(docRoot, "/", serialiser, persister, validator)
}
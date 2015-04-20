package trivial.rest

import com.twitter.finatra.{Controller, FinatraServer}
import trivial.rest.persistence.JsonOnFileSystem

class RestApp extends Controller {
  new Rest(this, "/", new JsonOnFileSystem("/src/test/resources"))
    .resource[Spaceship](GetAll)
    .resource[Vector](GetAll)
}

object RestApp extends FinatraServer {
  register(new RestApp)
}
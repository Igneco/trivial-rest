package trivial.rest

import com.twitter.finatra.{Controller, FinatraServer}
import trivial.rest.persistence.JsonOnFileSystem

import scala.reflect.io.Directory

class RestfulController extends Controller {
  new Rest(this, "/", new JsonOnFileSystem(Directory("src/test/resources")))
    .resource[Spaceship](GetAll)
    .resource[Vector](GetAll)
    .resource[Planet](GetAll)
}

object RestApp extends FinatraServer {
  register(new RestfulController)
}
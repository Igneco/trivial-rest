package trivial.rest

import com.twitter.finatra.{Controller, FinatraServer}
import trivial.rest.persistence.{Persister, JsonOnFileSystem}

import scala.reflect.io.Directory

class RestfulControllerExample(persister: Persister) extends Controller {
  new Rest("/", this, persister)
    .resource[Spaceship](GetAll, Post)
    .resource[Vector](GetAll)
    .resource[Planet](GetAll, Post)
    .resource[Foo](GetAll)
    .resource[Currency](GetAll)
    .resource[ExchangeRate](GetAll, Post)
}

object RestApp extends FinatraServer {
  register(new RestfulControllerExample(new JsonOnFileSystem(Directory("src/test/resources"))))
}
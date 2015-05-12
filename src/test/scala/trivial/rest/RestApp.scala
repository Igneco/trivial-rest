package trivial.rest

import com.twitter.finatra.{Controller, FinatraServer}
import trivial.rest.persistence.{Persister, JsonOnFileSystem}
import trivial.rest.serialisation.{Json4sSerialiser, Serialiser}

import scala.reflect.ClassTag
import scala.reflect.io.Directory

class RestfulControllerExample(persister: Persister) extends Controller {
  val serialiser = new Json4sSerialiser

  new Rest("/", this, serialiser, persister)
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
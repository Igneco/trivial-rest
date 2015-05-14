package trivial.rest

import com.twitter.finatra.{Controller, FinatraServer}
import trivial.rest.persistence.{Persister, JsonOnFileSystem}
import trivial.rest.serialisation.{Json4sSerialiser, Serialiser}

import scala.reflect.ClassTag
import scala.reflect.io.Directory

class RestfulControllerExample(serialiser: Serialiser, persister: Persister) extends Controller {
  new Rest("/", this, serialiser, persister)
    .resource[Spaceship](GetAll, Post)
    .resource[Vector](GetAll)
    .resource[Planet](GetAll, Post)
    .resource[Foo](GetAll, Post)
    .resource[Currency](GetAll)
    .resource[ExchangeRate](GetAll, Post)
}

object RestApp extends FinatraServer {
  val serialiser = new Json4sSerialiser
  val persister = new JsonOnFileSystem(Directory("src/test/resources"), serialiser)
  register(new RestfulControllerExample(serialiser, persister))
}
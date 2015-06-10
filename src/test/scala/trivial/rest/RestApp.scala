package trivial.rest

import com.twitter.finatra.{Controller, FinatraServer}
import trivial.rest.persistence.{Persister, JsonOnFileSystem}
import trivial.rest.serialisation.{Json4sSerialiser, Serialiser}

import scala.reflect.ClassTag
import scala.reflect.io.Directory

class RestExample(uriRoot: String,
                  controller: Controller,
                  serialiser: Serialiser,
                  persister: Persister) extends Rest(uriRoot, controller, serialiser, persister) {
    resource[Spaceship](GetAll, Post)
    resource[Vector](GetAll)
    resource[Planet](GetAll, Post)
    resource[Foo](GetAll, Post)
    resource[Currency](GetAll, Post)
    resource[ExchangeRate](GetAll, Post)
    resource[MetricPerson](GetAll, Post)
}

object RestApp extends FinatraServer {
  val serialiser = new Json4sSerialiser
  val persister = new JsonOnFileSystem(Directory("src/test/resources"), serialiser)
  val controller = new Controller {}
  val rest = new RestExample("/", controller, serialiser, persister)
  register(controller)
}
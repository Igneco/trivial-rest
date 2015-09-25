package trivial.rest.controller.finatra

import trivial.rest._
import trivial.rest.persistence.JsonOnFileSystem
import trivial.rest.serialisation.Json4sSerialiser
import trivial.rest.validation.RuleBasedRestValidator

import scala.reflect.io.Directory

class ExampleFinatraServer(docRoot: Directory, uriRoot: String) extends TrivialFinatraServer {
  val serialiser = new Json4sSerialiser
  val persister = new JsonOnFileSystem(docRoot, serialiser)
  val validator = new RuleBasedRestValidator()

  new Rest(uriRoot, controller, serialiser, persister, validator)
    .resource[Spaceship](GetAll, Post)
    .resource[Vector](GetAll)
    .resource[Planet](GetAll, Post)
    .resource[Foo](Get, GetAll, Post, Delete, Put)
    .resource[Currency](Get, GetAll, Post)
    .resource[ExchangeRate](Get, GetAll, Post)
    .resource[MetricPerson](GetAll, Post)
}

object ExampleFinatraApp extends ExampleFinatraServer(Directory("src/test/resources"), "/")
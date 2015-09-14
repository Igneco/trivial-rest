package trivial.rest

import trivial.rest.persistence.{JsonOnFileSystem, Persister}
import trivial.rest.serialisation.{Json4sSerialiser, Serialiser}
import trivial.rest.validation.{RestValidator, RuleBasedRestValidator}

import scala.reflect.io.Directory

class TestFinatraServer(docRoot: Directory,
                       uriRoot: String,
                       serialiser: Serialiser,
                       persister: Persister,
                       validator: RestValidator) extends TrivialFinatraServer {

  val rest = new Rest(uriRoot, controller, serialiser, persister, validator)
    .resource[Spaceship](GetAll, Post)
    .resource[Vector](GetAll)
    .resource[Planet](GetAll, Post)
    .resource[Foo](Get, GetAll, Post, Delete, Put)
    .resource[Currency](Get, GetAll, Post)
    .resource[ExchangeRate](Get, GetAll, Post)
    .resource[MetricPerson](GetAll, Post)
}

class MyExampleFintraServer(docRoot: Directory, uriRoot: String) extends TrivialFinatraServer {
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

object MyExampleFinatraApp extends MyExampleFintraServer(Directory("src/test/resources"), "/")

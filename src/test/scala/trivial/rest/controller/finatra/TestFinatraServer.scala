package trivial.rest.controller.finatra

import trivial.rest._
import trivial.rest.persistence.Persister
import trivial.rest.serialisation.Serialiser
import trivial.rest.validation.RestValidator

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
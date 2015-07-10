package trivial.rest

import com.twitter.finatra.{Controller, FinatraServer}
import trivial.rest.persistence.{Persister, JsonOnFileSystem}
import trivial.rest.serialisation.{Json4sSerialiser, Serialiser}
import trivial.rest.validation.{RuleBasedValidator, Validator}

import scala.reflect.io.Directory

class RestExample(uriRoot: String,
                  controller: Controller,
                  serialiser: Serialiser,
                  persister: Persister,
                  validator: Validator) extends Rest(uriRoot, controller, serialiser, persister, validator) {
    resource[Spaceship](GetAll, Post)
    resource[Vector](GetAll)
    resource[Planet](GetAll, Post)
    resource[Foo](Get, GetAll, Post, Delete, Put)
    resource[Currency](Get, GetAll, Post)
    resource[ExchangeRate](Get, GetAll, Post)
    resource[MetricPerson](GetAll, Post)
}

case class DemoApp(docRoot: Directory, uriRoot: String) extends FinatraServer {
  val serialiser = new Json4sSerialiser
  val persister = new JsonOnFileSystem(docRoot, serialiser)
  val controller = new Controller {}
  val validator = new RuleBasedValidator()
  val rest = new RestExample(uriRoot, controller, serialiser, persister, validator)
  register(controller)
}

object RestApp extends DemoApp(Directory("src/test/resources"), "/")
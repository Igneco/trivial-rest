package trivial.rest

import com.twitter.finatra.http.{Controller, HttpServer}
import com.twitter.finatra.http.filters.CommonFilters
import com.twitter.finatra.http.routing.HttpRouter
import trivial.rest.controller.finatra.{NonHidingExceptionsMapper, UsableController}
import trivial.rest.persistence.{JsonOnFileSystem, Persister}
import trivial.rest.serialisation.{Json4sSerialiser, Serialiser}
import trivial.rest.validation.{RuleBasedRestValidator, RestValidator}

import scala.reflect.io.Directory

class TrivialFinatraServer extends HttpServer {
  val controller = new UsableController {}

  override def configureHttp(router: HttpRouter) =
    router
      .exceptionMapper[NonHidingExceptionsMapper]
      .filter[CommonFilters]
      .add(controller)
}

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

package trivial.rest

import com.twitter.finatra.http.HttpServer
import com.twitter.finatra.http.filters.CommonFilters
import com.twitter.finatra.http.routing.HttpRouter
import trivial.rest.controller.finatra.{NonHidingExceptionsMapper, UsableController}
import trivial.rest.persistence.{JsonOnFileSystem, Persister}
import trivial.rest.serialisation.{Json4sSerialiser, Serialiser}
import trivial.rest.validation.{RuleBasedValidator, Validator}

import scala.reflect.io.Directory

class RestExample(uriRoot: String,
                  controller: UsableController,
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

case class DemoApp(docRoot: Directory, uriRoot: String) extends HttpServer {
  val serialiser = new Json4sSerialiser
  val persister = new JsonOnFileSystem(docRoot, serialiser)
  val controller = new UsableController {}
  val validator = new RuleBasedValidator()
  val rest = new RestExample(uriRoot, controller, serialiser, persister, validator)

  override def configureHttp(router: HttpRouter) =
    router
      .exceptionMapper[NonHidingExceptionsMapper]
      .filter[CommonFilters]
      .add(controller)
}

object RestApp extends DemoApp(Directory("src/test/resources"), "/")
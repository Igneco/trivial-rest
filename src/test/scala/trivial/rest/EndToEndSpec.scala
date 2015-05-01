package trivial.rest

import com.twitter.finagle.http.{MediaType, Request => FinagleRequest, Response => FinagleResponse}
import com.twitter.finagle.{Service, SimpleFilter}
import com.twitter.finatra.test.SpecHelper
import com.twitter.finatra.{Controller, FinatraServer}
import org.jboss.netty.handler.codec.http.HttpHeaders.Names._
import org.scalatest.{MustMatchers, WordSpec}
import trivial.rest.persistence.JsonOnFileSystem

import scala.reflect.io.Directory

class EndToEndSpec extends WordSpec with MustMatchers with SpecHelper {
  // TODO - CAS - 01/05/15 - Verify it, but I don't think we need filters any more. REMOVE.
  val monkeysFilter = new SimpleFilter[FinagleRequest, FinagleResponse] {
    def apply(request: FinagleRequest, service: Service[FinagleRequest, FinagleResponse]) =
      service(request) map { response =>
        response.contentType.filter(_ == MediaType.Json).foreach(ct => response.setContentType(ct, "MONKEYS"))
        response
      }
  }
  
  override def server = new FinatraServer {
    val controllerWithRest = new Controller {
      new Rest(this, "/", new JsonOnFileSystem(Directory("src/test/resources")))
        .resource[Spaceship](GetAll)
        .resource[Vector](GetAll)
    }
    addFilter(monkeysFilter)
    register(controllerWithRest)
  }

  /* TODO - CAS - 20/04/15 - This is a lie. Finatra's SpecHelper is broken:
  The charset value is not specified at runtime, it only works in this test
  The FileService intercepts the call and sets the default JSON content type
  In this test, the monkeysFilter is NOT called.
  */
  "Charset for JSON data is UTF-8" in {
    get("/vector")

    println(s"response.body: ${response.body}")
    response.getHeader(CONTENT_TYPE) must equal("application/json; charset=UTF-8")
  }
}
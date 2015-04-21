package trivial.rest

import com.twitter.finagle.http.MediaType
import com.twitter.finatra.Controller
import com.twitter.finatra.test.MockApp
import org.jboss.netty.handler.codec.http.HttpHeaders
import org.jboss.netty.handler.codec.http.HttpHeaders.Names
import org.scalatest.{MustMatchers, WordSpec}
import trivial.rest.persistence.JsonOnFileSystem

import scala.reflect.io.Directory

class RestfulApiSpec extends WordSpec with MustMatchers {

  "The root path provides a not-quite-hypertext list of supported resource types" in {
    val controllerWithRest = new Controller {
      new Rest(this, "/", new JsonOnFileSystem(Directory("src/test/resources"))) {
        resource[Spaceship](GetAll)
        resource[Vector](GetAll)
      }
    }
    val app = MockApp(controllerWithRest)

    validateJsonResponse(app, "/", """["spaceship","vector"]""")
  }

  "Registering a resource type as a GetAll allows bulk download" in {
    val controllerWithRest = new Controller {
      new Rest(this, "/", new JsonOnFileSystem(Directory("src/test/resources")))
        .resource[Spaceship](GetAll)
    }
    val app = MockApp(controllerWithRest)

    validateJsonResponse(app, "/spaceship.json", """[
                                                   |  {"id": "7", "name": "Enterprise", "personnel": 150, "vector": "24"},
                                                   |  {"id": "5", "name": "Laziness", "personnel": 20, "vector": "2"},
                                                   |  {"id": "3", "name": "Sloth", "personnel": 1, "vector": "4"},
                                                   |  {"id": "1", "name": "Gr€€d", "personnel": 5, "vector": "1"}
                                                   |]""".stripMargin)
  }

  "We send back a 404 for Resource types we don't support" in {
    val app = newUpApp

    // TODO - CAS - 20/04/15 - Test this for PUT, POST, etc
    val response = app.get(s"/petName")

    response.code must equal(404)
    response.body must equal("Resource type not supported: petName")
  }

  "PUTting a new item creates a unique ID for it" in {
    val app = newUpApp

    val response = app.put(s"/planet", body = """{"name": "Earth", "classification": "tolerable"}""")

    response.code must equal(200)
    response.body must equal("""{"id": "1", "name": "Earth", "classification": "tolerable"}""")
    response.getHeader(Names.CONTENT_TYPE) must equal(s"${MediaType.Json}; charset=UTF-8")
  }

  "You can't PUT an item with an ID - the system will choose one for you" in {
    val app = newUpApp

    val response = app.put(s"/planet", body = """{"id": "123", "name": "Earth", "classification": "tolerable"}""")

    // TODO - CAS - 21/04/15 - Find correct HTTP response code
    response.code must equal(400)
    response.body must equal("You can't PUT an item with an ID - the system will choose one for you")
  }

  "We can POST many items at once to be persisted" in {
    val app = newUpApp

    val response = app.post(s"/planet", body =
      """[
        |  {"name": "Mercury", "classification": "bloody hot"},
        |  {"name": "Venus", "classification": "also bloody hot"}
        |]""".stripMargin)
    
    fail("Do Put first")
  }

  // Remove the .json suffix in the URL, if that is all we are going to support
  // Caching
  
  def newUpApp = MockApp(new Controller {
    new Rest(this, "/", new JsonOnFileSystem(Directory("src/test/resources")))
      .resource[Spaceship](GetAll)
      .resource[Vector](GetAll)
      .resource[Planet](GetAll, Put)
  })

  private def validateJsonResponse(app: MockApp, path: String, expectedContents: String): Unit = {
    val response = app.get(path)

    response.body must equal(expectedContents)
    response.code must equal(200)
    response.getHeader(Names.CONTENT_TYPE) must equal(s"${MediaType.Json}; charset=UTF-8")
  }
}

case class Spaceship(id: String, name: String, personnel: Int, bearing: Vector)

case class Vector(id: String, angle: BigDecimal, magnitude: BigDecimal)

case class Planet(id: String, name: String, classification: String)
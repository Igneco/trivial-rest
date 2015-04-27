package trivial.rest

import com.twitter.finagle.http.MediaType
import com.twitter.finatra.Controller
import com.twitter.finatra.test.MockApp
import org.jboss.netty.handler.codec.http.HttpHeaders.Names
import org.scalatest.{MustMatchers, WordSpec}
import trivial.rest.TestDirectories._
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
                                                   |{"id": "7", "name": "Enterprise", "personnel": 150, "vector": "24"},
                                                   |{"id": "5", "name": "Laziness", "personnel": 20, "vector": "2"},
                                                   |{"id": "3", "name": "Sloth", "personnel": 1, "vector": "4"},
                                                   |{"id": "1", "name": "Gr€€d", "personnel": 5, "vector": "1"}
                                                   |]""".stripMargin)
  }

  "We send back a 404 for Resource types we don't support" in {
    val app = newUpApp

    // TODO - CAS - 20/04/15 - Test this for PUT, POST, etc
    val response = app.get(s"/petName")

    response.code must equal(404)
    response.body must equal("Resource type not supported: petName")
  }

  "POSTing a new item creates a unique ID for it" in {
    val app = newUpApp

    val response = app.post(s"/planet", body = """[{"name": "Earth", "classification": "tolerable"}]""")

    response.body must equal("""[{"id":"1","name":"Earth","classification":"tolerable"}]""")
    response.code must equal(200)
    response.getHeader(Names.CONTENT_TYPE) must equal(s"${MediaType.Json}; charset=UTF-8")
  }

  "You can't POST an item with an ID - the system will allocate an ID upon resource creation" in {
    val app = newUpApp

    val response = app.post(s"/planet", body = """[{"id": "123", "name": "Earth", "classification": "tolerable"}]""")

    response.code must equal(403)
    response.body must equal("You can't POST an item with an ID - the system will allocate an ID upon resource creation")
  }

  "We can POST many items at once to be persisted" in {
    val app = newUpApp

    val postResponse = app.post(s"/planet", body = """[
                                                     |  {"name": "Mercury", "classification": "bloody hot"},
                                                     |  {"name": "Venus", "classification": "also bloody hot"}
                                                     |]""".stripMargin)
    
    postResponse.body must equal("""[{"id":"1","name":"Mercury","classification":"bloody hot"},{"id":"2","name":"Venus","classification":"also bloody hot"}]""")
    postResponse.code must equal(200)

    val getResponse = app.get("/planet")
    
    getResponse.code must equal(200)
    getResponse.body must equal( """[{"id":"1","name":"Mercury","classification":"bloody hot"},{"id":"2","name":"Venus","classification":"also bloody hot"}]""")
  }

  "Each successive item gets a new, unique, sequence ID" in {
    val app = newUpApp

    app.post(s"/planet", body = """[{"name": "Earth","classification": "tolerable"}]""")
    app.post(s"/planet", body = """[{"name": "Mars","classification": "chilly"}]""")
    app.post(s"/planet", body = """[{"name": "Uranus","classification": "a little dark"}]""")
    
    val response = app.get("/planet")

    response.code must equal(200)
    response.body must equal("""[{"id":"1","name":"Earth","classification":"tolerable"},{"id":"2","name":"Mars","classification":"chilly"},{"id":"3","name":"Uranus","classification":"a little dark"}]""".stripMargin)
  }

  "TODO - Return a 405 for HTTP methods that are not supported" in {
    /*
    10.4.6 405 Method Not Allowed

The method specified in the Request-Line is not allowed for the resource identified by the Request-URI. The response MUST include an Allow header containing a list of valid methods for the requested resource.
     */
    fail("TODO - Return a 405 for HTTP methods that are not supported")
  }

  // TODO - CAS - 27/04/15:
  // Remove the .json suffix in the URL, if that is all we are going to support
  // Caching
  
  def newUpApp = MockApp(new Controller {
    new Rest(this, "/", new JsonOnFileSystem(nextTestDir))
      .resource[Spaceship](GetAll)
      .resource[Vector](GetAll)
      .resource[Planet](GetAll, Post)
  })

  private def validateJsonResponse(app: MockApp, path: String, expectedContents: String): Unit = {
    val response = app.get(path)

    response.body must equal(expectedContents)
    response.code must equal(200)
    response.getHeader(Names.CONTENT_TYPE) must equal(s"${MediaType.Json}; charset=UTF-8")
  }
}

case class Spaceship(id: Option[String], name: String, personnel: Int, bearing: Vector) extends Restable {
  override def withId(id: String) = copy(id = Some(id))
}

case class Vector(id: Option[String], angle: BigDecimal, magnitude: BigDecimal) extends Restable {
  override def withId(id: String) = copy(id = Some(id))
}

case class Planet(id: Option[String], name: String, classification: String) extends Restable {
  override def withId(id: String) = copy(id = Some(id))
}
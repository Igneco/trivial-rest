package trivial.rest

import com.twitter.finagle.http.MediaType
import com.twitter.finatra.Controller
import com.twitter.finatra.test.MockApp
import org.jboss.netty.handler.codec.http.HttpHeaders.Names
import org.scalamock.scalatest.MockFactory
import org.scalatest.{BeforeAndAfterAll, MustMatchers, WordSpec}
import trivial.rest.TestDirectories._
import trivial.rest.persistence.{Persister, JsonOnFileSystem}

import scala.reflect.io.Directory

class RestfulApiSpec extends WordSpec with MustMatchers with BeforeAndAfterAll with MockFactory {

  override protected def beforeAll() = cleanTestDirs()
  override protected def afterAll()  = beforeAll()

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
    val persisterMock: Persister = mock[Persister]

    val controllerWithRest = new Controller {
      new Rest(this, "/", persisterMock)
        .resource[Foo](GetAll)
    }
    val app = MockApp(controllerWithRest)

    val expectedData = Right(Seq(
      Foo(Some("1"), "bar"),
      Foo(Some("2"), "baz"),
      Foo(Some("3"), "bazaar")
    ))

    (persisterMock.loadAll[Foo](_: String)(_: Manifest[Foo])).expects("foo", *).returning(expectedData)

    validateJsonResponse(app, "/foo.json", """[{"id":"1","bar":"bar"},{"id":"2","bar":"baz"},{"id":"3","bar":"bazaar"}]""")
  }

  // TODO - CAS - 02/05/15 - Mock the persister in all tests
  // TODO - CAS - 03/05/15 - Add a serialiser for each T that just writes the ID as a String. Make this configurable (some people will want the whole tree written)
  
  "TODO - Resources can reference other resources during deserialisation" in {
    pending
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

  "Return a 405 for HTTP methods that are not supported" in {
    val app = newUpApp

    val response = app.post("/spaceship", body = "")

    response.code must equal(405)
    response.body must equal( """Method not allowed: POST. Methods supported by /spaceship are: GET all""")
  }

  "TODO - There is a way to migrate stucture changes" in {
    pending
  }

  "TODO - There is a way to pre-populate resources, so they are not empty when they are first released" in {
    pending
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
package trivial.rest

import com.twitter.finagle.http.MediaType
import com.twitter.finatra.Controller
import com.twitter.finatra.test.MockApp
import org.jboss.netty.handler.codec.http.HttpHeaders
import org.scalatest.{MustMatchers, WordSpec}

class RestfulApiSpec extends WordSpec with MustMatchers {

  "The root path provides a not-quite-hypertext list of supported resource types" in {
    val controllerWithRest = new Controller with Rest {
      resource[Spaceship](GetAll)
      resource[Vector](GetAll)
    }
    val app = MockApp(controllerWithRest)

    validateJsonResponse(app, "/", """["spaceship","vector"]""")
  }

  "Registering a resource type as a GetAll allows bulk download" in {
    val controllerWithRest = new Controller with Rest {
      resource[Spaceship](GetAll)
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
    val controllerWithRest = new Controller with Rest {
      resource[Spaceship](GetAll)
    }
    val app = MockApp(controllerWithRest)

    val response = app.get(s"/petNames.json")

    response.code must equal(404)
    response.body must equal("Resource type not supported: /petNames.json")
  }

  // UTF-8
  /*
  val utf8Json = s"${MediaType.Json}; charset=UTF-8"
  val serialiser = DefaultJacksonJsonSerializer
   */
  
  // Caching
  // Persistence
  // Setting URI path root
  // Setting doc route programmatically, not via System.setProperty("com.twitter.finatra.config.docRoot", "src/test/resources")

  private def validateJsonResponse(app: MockApp, path: String, expectedContents: String): Unit = {
    val response = app.get(path)

    response.body must equal(expectedContents)
    response.code must equal(200)
    response.getHeader(HttpHeaders.Names.CONTENT_TYPE) must equal(s"${MediaType.Json}; charset=UTF-8")
  }
}

case class Spaceship(id: String, name: String, personnel: Int, bearing: Vector)

case class Vector(id: String, angle: BigDecimal, magnitude: BigDecimal)
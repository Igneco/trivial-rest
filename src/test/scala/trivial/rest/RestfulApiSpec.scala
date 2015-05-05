package trivial.rest

import java.util.concurrent.atomic.AtomicInteger

import com.twitter.finagle.http.MediaType
import com.twitter.finatra.Controller
import com.twitter.finatra.test.MockApp
import org.jboss.netty.handler.codec.http.HttpHeaders.Names
import org.scalamock.scalatest.MockFactory
import org.scalatest.{BeforeAndAfterAll, MustMatchers, WordSpec}
import trivial.rest.TestDirectories._
import trivial.rest.configuration.Config
import trivial.rest.persistence.Persister

class RestfulApiSpec extends WordSpec with MustMatchers with BeforeAndAfterAll with MockFactory {

  override protected def beforeAll() = cleanTestDirs()
  override protected def afterAll()  = beforeAll()

  "The root path provides a not-quite-hypertext list of supported resource types" in {
    val fixture = new RestApiFixture()

    val response = fixture.app.get("/")

    response.body must equal("""["foo","planet","spaceship","vector"]""")
    response.code must equal(200)
    response.getHeader(Names.CONTENT_TYPE) must equal(s"${MediaType.Json}; charset=UTF-8")
  }

  "Registering a resource type as a GetAll allows bulk download" in {
    val fixture = new RestApiFixture()

    fixture.persister_expects_loadAll("foo", Right(Seq(
      Foo(Some("1"), "bar"),
      Foo(Some("2"), "baz"),
      Foo(Some("3"), "bazaar")
    )))

    val response = fixture.app.get("/foo")

    response.body must equal("""[{"id":"1","bar":"bar"},{"id":"2","bar":"baz"},{"id":"3","bar":"bazaar"}]""")
    response.code must equal(200)
    response.getHeader(Names.CONTENT_TYPE) must equal(s"${MediaType.Json}; charset=UTF-8")
  }

  // TODO - CAS - 03/05/15 - Add a serialiser for each T that just writes the ID as a String. Make this configurable (some people will want the whole tree written)
  "TODO - We can send responses in flat id-referenced form" in {
    val fixture = new RestApiFixture(Config(flattenNestedResources = true))

    implicit def toBigDecimal(str: String): BigDecimal = BigDecimal(str)
    val vector = Vector(Some("24"), "79", "0.4")
    val spaceship = Spaceship(Some("1"), "Enterprise", 150, vector)

    fixture.persister_expects_loadAll("spaceship", Right(Seq(spaceship)))

    val response = fixture.app.get("/spaceship")

    response.body must equal("""[{"id":"1","name":"Enterprise","personnel":150,"bearing":"24"}]""")
    response.code must equal(200)
    response.getHeader(Names.CONTENT_TYPE) must equal(s"${MediaType.Json}; charset=UTF-8")
  }

  "TODO - We can send responses in nested form" in {
    val fixture = new RestApiFixture(Config(flattenNestedResources = false))
    pending
  }

  "We can accept input in nested or flat id-referenced form" in {
    // post "[{"id":"7","name":"Enterprise","personnel":150,"bearing":{"id":"24","angle":79,"magnitude":0.4}}]"
    // post "[{"id":"1","name":"Enterprise","personnel":150,"bearing":"1"}]"
    pending
  }
  
  "We can choose to check that flat references to other resources exist, or to ignore them" in {
    pending
  }

  "We send back a 404 for Resource types we don't support" in {
    val app = new RestApiFixture().app

    // TODO - CAS - 20/04/15 - Test this for PUT, POST, etc
    val response = app.get(s"/petName")

    response.code must equal(404)
    response.body must equal("Resource type not supported: petName")
  }

  "POSTing a new item creates a unique ID for it" in {
    val fixture = new RestApiFixture()
    fixture.persister_expects_save("planet", Seq(Planet(Some("1"), "Earth", "tolerable")))
    fixture.persister_expects_nextSequenceNumber(1)

    val response = fixture.app.post(s"/planet", body = """[{"name": "Earth", "classification": "tolerable"}]""")

    response.body must equal("""[{"id":"1","name":"Earth","classification":"tolerable"}]""")
    response.code must equal(200)
    response.getHeader(Names.CONTENT_TYPE) must equal(s"${MediaType.Json}; charset=UTF-8")
  }

  "You can't POST an item with an ID - the system will allocate an ID upon resource creation" in {
    val app = new RestApiFixture().app

    val response = app.post(s"/planet", body = """[{"id": "123", "name": "Earth", "classification": "tolerable"}]""")

    response.code must equal(403)
    response.body must equal("You can't POST an item with an ID - the system will allocate an ID upon resource creation")
  }

  "We can POST many items at once to be persisted" in {
    val fixture = new RestApiFixture()
    fixture.persister_expects_nextSequenceNumber(2)
    fixture.persister_expects_save("planet", Seq(
      Planet(Some("1"), "Mercury", "bloody hot"),
      Planet(Some("2"), "Venus", "also bloody hot")
    ))

    val postResponse = fixture.app.post(s"/planet", body = """[
                                                     |  {"name": "Mercury", "classification": "bloody hot"},
                                                     |  {"name": "Venus", "classification": "also bloody hot"}
                                                     |]""".stripMargin)
    
    postResponse.body must equal("""[{"id":"1","name":"Mercury","classification":"bloody hot"},{"id":"2","name":"Venus","classification":"also bloody hot"}]""")
    postResponse.code must equal(200)
  }

  "Each successive item gets a new, unique, sequence ID" in {
    val fixture = new RestApiFixture()
    fixture.persister_expects_save("planet", Seq(Planet(Some("1"), "Earth", "tolerable")))
    fixture.persister_expects_save("planet", Seq(Planet(Some("2"), "Mars", "chilly")))
    fixture.persister_expects_save("planet", Seq(Planet(Some("3"), "Uranus", "a little dark")))

    fixture.persister_expects_nextSequenceNumber(3)

    fixture.app.post(s"/planet", body = """[{"name": "Earth","classification": "tolerable"}]""")
    fixture.app.post(s"/planet", body = """[{"name": "Mars","classification": "chilly"}]""")
    fixture.app.post(s"/planet", body = """[{"name": "Uranus","classification": "a little dark"}]""")
  }

  "Return a 405 for HTTP methods that are not supported" in {
    val app = new RestApiFixture().app

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
  
  class RestApiFixture(config: Config = Config()) {
    val persisterMock: Persister = mock[Persister]
    val controllerWithRest = new Controller {
      new Rest("/", this, persisterMock)
        .resource[Spaceship](GetAll)
        .resource[Vector](GetAll)
        .resource[Planet](GetAll, Post)
        .resource[Foo](GetAll)
    }
    val app = MockApp(controllerWithRest)

    def persister_expects_loadAll[T <: Restable[T]](expectedParam: String, returns: Either[Failure, Seq[T]]) = {
      (persisterMock.loadAll[T](_: String)(_: Manifest[T])).expects(expectedParam, *).returning(returns)
    }

    val sequence = new AtomicInteger(0)
    def persister_expects_nextSequenceNumber(highest: Int) = (persisterMock.nextSequenceNumber _).expects().onCall(() => sequence.incrementAndGet()).repeat(highest)

    def persister_expects_save[T <: Restable[T]](expectedResource: String, expectedSeq: Seq[T]) = {
      (persisterMock.save[T](_: String, _: Seq[T])(_: Manifest[T])).expects(expectedResource, expectedSeq, *).returning(Right(expectedSeq))
    }
  }
}
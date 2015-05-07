package trivial.rest

import java.util.concurrent.atomic.AtomicInteger

import com.twitter.finagle.http.MediaType
import com.twitter.finatra.Controller
import com.twitter.finatra.test.MockApp
import org.jboss.netty.handler.codec.http.HttpHeaders.Names
import org.json4s.Formats
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

    response.body must equal("""["currency","exchangerate","foo","planet","spaceship","vector"]""")
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

  "We can send responses in flat id-referenced form" in {
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
    pending
    val fixture = new RestApiFixture(Config(flattenNestedResources = false))
  }

  "We can accept input in flat id-referenced form" in {
    val fixture = new RestApiFixture()
    fixture.persister_expects_save("spaceship", Seq(Spaceship(Some("1"), "Enterprise", 150, Vector(Some("1"), BigDecimal("33.3"), BigDecimal("1.4")))))
    fixture.persister_expects_nextSequenceNumber(1)
    fixture.persister_expects_loadAll("vector", Right(Seq(Vector(Some("1"), BigDecimal("33.3"), BigDecimal("1.4")))))

    val response = fixture.app.post(s"/spaceship", body = """[{"name":"Enterprise","personnel":150,"bearing":"1"}]""")

    response.body must equal("""[{"id":"1","name":"Enterprise","personnel":150,"bearing":"1"}]""")
    response.code must equal(200)
    response.getHeader(Names.CONTENT_TYPE) must equal(s"${MediaType.Json}; charset=UTF-8")
  }

  "We can accept input in nested form" in {
    // post "[{"id":"7","name":"Enterprise","personnel":150,"bearing":{"id":"24","angle":79,"magnitude":0.4}}]"
    pending
  }
  
  "We can choose to check that flat references to other resources exist, or to ignore them" in {
    pending
  }
  
  "We can choose to check that nested resources exist, or to ignore them" in {
    pending
  }
  
  "We can serialise (to an HTTP response) a resource which embeds another resource" in {
    val fixture = new RestApiFixture()

    fixture.persister_expects_loadAll("exchangerate", Right(Seq(
      ExchangeRate(Some("1"), BigDecimal("33.3"), Currency(Some("22"), "GBP", "£"))
    )))

    val response = fixture.app.get("/exchangerate")

    response.body must equal("""[{"id":"1","rate":33.3,"currency":"22"}]""")
    response.code must equal(200)
    response.getHeader(Names.CONTENT_TYPE) must equal(s"${MediaType.Json}; charset=UTF-8")
  }

  "We can deserialise characters such as € and £ into non-escaped unicode Strings" in {
    pending
    val fixture = new RestApiFixture()

    fixture.persister_expects_loadAll("currency", Right(Seq(
      Currency(Some("1"), "EUR", "€"),
      Currency(Some("2"), "GBP", "£"),
      Currency(Some("3"), "USD", "$")
    )))

    val response = fixture.app.get("/currency")

    response.body must equal("""[{"id":"1","isoName":"EUR","symbol":"€"},{"id":"2","isoName":"GBP","symbol":"£"},{"id":"3","isoName":"USD","symbol":"$"}]""")
    response.code must equal(200)
    response.getHeader(Names.CONTENT_TYPE) must equal(s"${MediaType.Json}; charset=UTF-8")
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

    val response = app.put("/spaceship", body = "")

    response.code must equal(405)
    response.body must equal( """Method not allowed: PUT. Methods supported by /spaceship are: GET all, POST""")
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
    val controllerWithRest = new RestfulControllerExample(persisterMock)
    val app = MockApp(controllerWithRest)

    def persister_expects_loadAll[T <: Resource[T]](expectedParam: String, returns: Either[Failure, Seq[T]]) = {
      (persisterMock.loadAll[T](_: String)(_: Manifest[T], _: Formats)).expects(expectedParam, *, *).returning(returns)
    }

    val sequence = new AtomicInteger(0)
    def persister_expects_nextSequenceNumber(highest: Int) = (persisterMock.nextSequenceNumber _).expects().onCall(() => sequence.incrementAndGet()).repeat(highest)

    def persister_expects_save[T <: Resource[T]](expectedResource: String, expectedSeq: Seq[T]) = {
      (persisterMock.save[T](_: String, _: Seq[T])(_: Manifest[T], _: Formats)).expects(expectedResource, expectedSeq, *, *).returning(Right(expectedSeq))
    }
  }
}
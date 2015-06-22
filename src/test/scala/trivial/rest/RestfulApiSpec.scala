package trivial.rest

import com.twitter.finagle.http.MediaType
import com.twitter.finatra.Controller
import com.twitter.finatra.test.MockApp
import org.jboss.netty.handler.codec.http.HttpHeaders.Names
import org.json4s.Formats
import org.scalamock.scalatest.MockFactory
import org.scalatest.{BeforeAndAfterAll, MustMatchers, WordSpec}
import trivial.rest.RestApp._
import trivial.rest.TestDirectories._
import trivial.rest.persistence.Persister
import trivial.rest.serialisation.Serialiser

import scala.collection.mutable
import scala.reflect.ClassTag

class RestfulApiSpec extends WordSpec with MustMatchers with MockFactory {

  "The root path provides a not-quite-hypertext list of supported resource types" in {
    val fixture = new RestApiFixture()

    val response = fixture.app.get("/")

    response.body must equal("""["currency","exchangerate","foo","metricperson","planet","spaceship","vector"]""")
    response.code must equal(200)
    response.getHeader(Names.CONTENT_TYPE) must equal(s"${MediaType.Json}; charset=UTF-8")
  }

  val seqFoos = Seq(
    Foo(Some("1"), "bar"),
    Foo(Some("2"), "baz"),
    Foo(Some("3"), "bazaar")
  )

  "Registering a resource type for GET All allows bulk download of JSON data" in {
    val fixture = new RestApiFixture()

    fixture.persister_expects_loadAll("foo", Right(seqFoos))
    fixture.serialiser_expects_serialise[Foo]

    val response = fixture.app.get("/foo")

    response.body must equal("<A serialised Seq[Foo]>")
    response.code must equal(200)
    response.getHeader(Names.CONTENT_TYPE) must equal(s"${MediaType.Json}; charset=UTF-8")
  }

  "POSTing a new item saves it to the persister" in {
    val fixture = new RestApiFixture()
    val foo = Foo(None, "Baz")
    fixture.persister_expects_nextSequenceNumber("555")
    fixture.persister_expects_save("spaceship", Seq(foo.withId(Some("555"))))
    fixture.serialiser_expects_deserialise[Foo]("<A serialised Foo>", Seq(foo))

    val response = fixture.app.post(s"/spaceship", body = "<A serialised Foo>")

    response.body must equal("""{"addedCount":"1"}""")
    response.code must equal(200)
    response.getHeader(Names.CONTENT_TYPE) must equal(s"${MediaType.Json}; charset=UTF-8")
  }

  "POSTing a new item request an ID for it from the Persister" in {
    val fixture = new RestApiFixture()

    fixture.persister_expects_nextSequenceNumber("666")

    fixture.app.post(s"/planet", body = """[{"name": "Earth", "classification": "tolerable"}]""")
  }

  "We send back a 404 for Resource types we don't support" in {
    val app = new RestApiFixture().app

    // TODO - CAS - 20/04/15 - Test this for PUT, POST, etc
    val response = app.get(s"/petName")

    response.body must equal("Resource type not supported: petName")
    response.code must equal(404)
  }

  "Validation failure. You can't POST an item with an ID - the system will allocate an ID upon resource creation" in {
    val fixture = new RestApiFixture()
    fixture.serialiser_expects_deserialise[Planet]("<A serialised Planet>", Seq(Planet(Some("123"), "Earth", "tolerable")))

    val response = fixture.app.post(s"/planet", body = "<A serialised Planet>")

    response.body must include ("Validation failure. You can't POST an item with an ID - the system will allocate an ID upon resource creation")
    response.code must equal(403)
  }

  "POSTing items returns an updated count" in {
    val fixture = new RestApiFixture()

    val somePlanets = Seq(
      Planet(None, "Mercury", "bloody hot"),
      Planet(None, "Venus", "also bloody hot")
    )

    val somePlanetsWithIds = Seq(
      Planet(Some("1"), "Mercury", "bloody hot"),
      Planet(Some("2"), "Venus", "also bloody hot")
    )

    fixture.serialiser_expects_deserialise[Planet]("<Some serialised Planets>", somePlanets)
    fixture.persister_expects_nextSequenceNumber("1", "2")
    fixture.persister_expects_save("planet", somePlanetsWithIds)

    val postResponse = fixture.app.post(s"/planet", body = "<Some serialised Planets>")

    postResponse.body must equal("""{"addedCount":"2"}""")
    postResponse.code must equal(200)
  }

  "Return a 405 for HTTP methods that are not supported" in {
    val app = new RestApiFixture().app

    val response = app.put("/spaceship", body = "")

    response.code must equal(405)
    response.body must equal( """Method not allowed: PUT. Methods supported by /spaceship are: GET all, POST""")
  }

//  "We can GET a single item by ID" in {
//    val fixture = new RestApiFixture()
//
//    fixture.persister_expects_load("foo", "3")
//    fixture.serialiser_expects_serialise[Foo]
//
//    val response = fixture.app.get("/foo/3")
//
//    response.body must equal("<A Foo>")
//    response.code must equal(200)
//    response.getHeader(Names.CONTENT_TYPE) must equal(s"${MediaType.Json}; charset=UTF-8")
//  }

  // TODO - CAS - 27/04/15:
  // Caching spec

  class RestApiFixture() {
    val persisterMock: Persister = mock[Persister]
    val formats: Formats = mock[Formats]
    val serialiserMock: Serialiser = mock[Serialiser]

    serialiser_expects_registerResource[Spaceship]
    serialiser_expects_registerResource[Vector]
    serialiser_expects_registerResource[Planet]
    serialiser_expects_registerResource[ExchangeRate]
    serialiser_expects_registerResource[Foo]
    serialiser_expects_registerResource[Currency]
    serialiser_expects_registerResource[MetricPerson]

    serialiser_expects_formatsExcept[Spaceship]
    serialiser_expects_formatsExcept[Vector]
    serialiser_expects_formatsExcept[Planet]
    serialiser_expects_formatsExcept[ExchangeRate]
    serialiser_expects_formatsExcept[Foo]
    serialiser_expects_formatsExcept[Currency]

    val controller = new Controller {}
    val rest = new RestExample("/", controller, serialiserMock, persisterMock)
    val app = MockApp(controller)

    def serialiser_expects_registerResource[T <: Resource[T] : ClassTag] = {
      (serialiserMock.registerResource[T] (_: Formats => Either[Failure, Seq[T]])(_: ClassTag[T])).expects(*,*)
    }

    def serialiser_expects_formatsExcept[T <: Resource[T] : ClassTag] = {
      (serialiserMock.formatsExcept[T] (_: ClassTag[T])).expects(*).returning(formats).anyNumberOfTimes()
    }

    def serialiser_expects_serialise[T <: AnyRef : ClassTag] = {
      (serialiserMock.serialise[T](_: Seq[T])(_: ClassTag[T])).expects(*, *).returning(s"<A serialised Seq[${Classy.name[T]}]>")
    }

    def serialiser_expects_deserialise[T <: Resource[T] : ClassTag](body: String, returns: Seq[T]) = {
      (serialiserMock.deserialise[T](_: String)(_: Manifest[T])).expects(body, *).returning(Right(returns))
    }

    def persister_expects_loadAll[T <: Resource[T]](expectedParam: String, returns: Either[Failure, Seq[T]]) = {
      (persisterMock.loadAll[T](_: String)(_: Manifest[T])).expects(expectedParam, *).returning(returns)
    }

    val sequence = new mutable.Queue[String]()
    def persister_expects_nextSequenceNumber(values: String*) = {
      sequence.enqueue(values:_*)
      (persisterMock.nextSequenceId _).expects().onCall(() => sequence.dequeue()).anyNumberOfTimes()
    }

    def persister_expects_save[T <: Resource[T]](expectedResource: String, expectedSeq: Seq[T]) = {
      (persisterMock.save[T](_: String, _: Seq[T])(_: Manifest[T])).expects(expectedResource, expectedSeq, *).returning(Right(expectedSeq.size))
    }
  }
}

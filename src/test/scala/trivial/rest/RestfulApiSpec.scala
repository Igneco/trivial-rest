package trivial.rest

import com.twitter.finagle.http.MediaType
import com.twitter.finatra.test.MockApp
import org.jboss.netty.handler.codec.http.HttpHeaders.Names
import org.json4s.Formats
import org.scalamock.scalatest.MockFactory
import org.scalatest.{BeforeAndAfterAll, MustMatchers, WordSpec}
import trivial.rest.TestDirectories._
import trivial.rest.persistence.Persister
import trivial.rest.serialisation.Serialiser

import scala.collection.mutable
import scala.reflect.ClassTag

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

  "Registering a resource type for GET All allows bulk download of JSON data" in {
    val fixture = new RestApiFixture()

    val foos = Seq(
      Foo(Some("1"), "bar"),
      Foo(Some("2"), "baz"),
      Foo(Some("3"), "bazaar")
    )

    fixture.persister_expects_loadAll("foo", Right(foos))
    fixture.serialiser_expects_serialise[Foo]

    val response = fixture.app.get("/foo")

    response.body must equal("<A serialised Foo>")
    response.code must equal(200)
    response.getHeader(Names.CONTENT_TYPE) must equal(s"${MediaType.Json}; charset=UTF-8")
  }

  "POSTing a new item saves it to the persister" in {
    val fixture = new RestApiFixture()
    val foo = Foo(None, "Baz")
    fixture.persister_expects_nextSequenceNumber(555)
    fixture.persister_expects_save("spaceship", Seq(foo.withId("555")))
    fixture.serialiser_expects_deserialise[Foo]("<A serialised Foo>", Seq(foo))

    val response = fixture.app.post(s"/spaceship", body = "<A serialised Foo>")

    response.body must equal("""{"addedCount":"1"}""")
    response.code must equal(200)
    response.getHeader(Names.CONTENT_TYPE) must equal(s"${MediaType.Json}; charset=UTF-8")
  }

  "POSTing a new item request an ID for it from the Persister" in {
    val fixture = new RestApiFixture()

    fixture.persister_expects_nextSequenceNumber(666)

    fixture.app.post(s"/planet", body = """[{"name": "Earth", "classification": "tolerable"}]""")
  }

  "We send back a 404 for Resource types we don't support" in {
    val app = new RestApiFixture().app

    // TODO - CAS - 20/04/15 - Test this for PUT, POST, etc
    val response = app.get(s"/petName")

    response.body must equal("Resource type not supported: petName")
    response.code must equal(404)
  }

  "You can't POST an item with an ID - the system will allocate an ID upon resource creation" in {
    val fixture = new RestApiFixture()
    fixture.serialiser_expects_deserialise[Planet]("<A serialised Planet>", Seq(Planet(Some("123"), "Earth", "tolerable")))

    val response = fixture.app.post(s"/planet", body = "<A serialised Planet>")

    response.body must equal("You can't POST an item with an ID - the system will allocate an ID upon resource creation")
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
    fixture.persister_expects_nextSequenceNumber(1,2)
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

  "TODO - There is a way to migrate stucture changes" in {
    pending
  }

  "TODO - There is a way to pre-populate resources, so they are not empty when they are first released" in {
    pending
  }

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

    serialiser_expects_formatsExcept[Spaceship]
    serialiser_expects_formatsExcept[Vector]
    serialiser_expects_formatsExcept[Planet]
    serialiser_expects_formatsExcept[ExchangeRate]
    serialiser_expects_formatsExcept[Foo]
    serialiser_expects_formatsExcept[Currency]

    val controllerWithRest = new RestfulControllerExample(serialiserMock, persisterMock)
    val app = MockApp(controllerWithRest)

    def serialiser_expects_registerResource[T <: Resource[T] : ClassTag] = {
      (serialiserMock.registerResource[T] (_: Formats => Either[Failure, Seq[T]])(_: ClassTag[T])).expects(*,*)
    }

    def serialiser_expects_formatsExcept[T <: Resource[T] : ClassTag] = {
      (serialiserMock.formatsExcept[T] (_: ClassTag[T])).expects(*).returning(formats).anyNumberOfTimes()
    }

    def serialiser_expects_serialise[T <: Resource[T] : ClassTag] = {
      (serialiserMock.serialise[T](_: Seq[T])(_: ClassTag[T])).expects(*, *).returning(s"<A serialised ${Classy.name[T]}>")
    }

    def serialiser_expects_deserialise[T <: Resource[T] : ClassTag](body: String, returns: Seq[T]) = {
      (serialiserMock.deserialise[T](_: String)(_: Manifest[T])).expects(body, *).returning(Right(returns))
    }

    def persister_expects_loadAll[T <: Resource[T]](expectedParam: String, returns: Either[Failure, Seq[T]]) = {
      (persisterMock.loadAll[T](_: String)(_: Manifest[T], _: Formats)).expects(expectedParam, *, *).returning(returns)
    }

    val sequence = new mutable.Queue[Int]()
    def persister_expects_nextSequenceNumber(values: Int*) = {
      sequence.enqueue(values:_*)
      (persisterMock.nextSequenceNumber _).expects().onCall(() => sequence.dequeue()).anyNumberOfTimes()
    }

    def persister_expects_save[T <: Resource[T]](expectedResource: String, expectedSeq: Seq[T]) = {
      (persisterMock.save[T](_: String, _: Seq[T])(_: Manifest[T], _: Formats)).expects(expectedResource, expectedSeq, *, *).returning(Right(expectedSeq.size))
    }
  }
}

/*
It went horribly wrong: org.scalatest.exceptions.TestFailedException: Unexpected call:

save(spaceship, List(), trivial.rest.Spaceship)
save(spaceship, List(Spaceship(Some(1),Enterprise,150,Vector(Some(1),33.3,1.4))), *, *)

Expected:
inAnyOrder {
  registerResource(*, *) once (called once)
  registerResource(*, *) once (called once)
  registerResource(*, *) once (called once)
  registerResource(*, *) once (called once)
  registerResource(*, *) once (called once)
  registerResource(*, *) once (called once)
  formatsExcept(*) any number of times (called once)
  formatsExcept(*) any number of times (never called)
  formatsExcept(*) any number of times (never called)
  formatsExcept(*) any number of times (never called)
  formatsExcept(*) any number of times (never called)
  formatsExcept(*) any number of times (never called)
  save(spaceship, List(Spaceship(Some(1),Enterprise,150,Vector(Some(1),33.3,1.4))), *, *) once (never called - UNSATISFIED)
  nextSequenceNumber() any number of times (never called)
  loadAll(vector, *, *) once (never called - UNSATISFIED)
  deserialise(*, *) once (called once)
}

Actual:
  registerResource(<function1>, trivial.rest.Spaceship)
  registerResource(<function1>, trivial.rest.Vector)
  registerResource(<function1>, trivial.rest.Planet)
  registerResource(<function1>, trivial.rest.Foo)
  registerResource(<function1>, trivial.rest.Currency)
  registerResource(<function1>, trivial.rest.ExchangeRate)
  deserialise([{"name":"Enterprise","personnel":150,"bearing":"1"}], trivial.rest.Spaceship)
  formatsExcept(trivial.rest.Spaceship)
  save(spaceship, List(), trivial.rest.Spaceship, trivial.rest.RestfulApiSpec$RestApiFixture$$anon$2@35e5d0e5)

*/
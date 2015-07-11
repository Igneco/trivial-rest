package trivial.rest

import com.twitter.finagle.http.MediaType
import com.twitter.finatra.Controller
import com.twitter.finatra.test.{MockApp, MockResult}
import org.jboss.netty.handler.codec.http.HttpHeaders.Names
import org.json4s.Formats
import org.scalamock.scalatest.MockFactory
import org.scalatest.{MustMatchers, WordSpec}
import trivial.rest.persistence.Persister
import trivial.rest.serialisation.Serialiser
import trivial.rest.validation.Validator

import scala.collection.mutable
import scala.reflect.ClassTag

class RestfulApiSpec extends WordSpec with MustMatchers with MockFactory {

  implicit class ExpectedSuccess(response: MockResult) {
    def -->(expectedBody: String): Unit = {
      response.body must equal(expectedBody)
      response.code must equal(200)
      response.getHeader(Names.CONTENT_TYPE) must equal(s"${MediaType.Json}; charset=UTF-8")
    }
  }

  "The root path provides a not-quite-hypertext list of supported resource types" in new RestApiFixture() {
    app.get("/") --> """["currency","exchangerate","foo","metricperson","planet","spaceship","vector"]"""
  }

  val seqFoos = Seq(
    Foo(Some("1"), "bar"),
    Foo(Some("2"), "baz"),
    Foo(Some("3"), "bazaar")
  )

  "Registering a resource type for GET All allows bulk download of JSON data" in new RestApiFixture() {
    persister_expects_read("foo", returns = Right(seqFoos))
    serialiser_expects_serialise[Foo]

    app.get("/foo") --> "<A serialised Seq[Foo]>"
  }

  // TODO - CAS - 08/07/15 - Change the return type to: {added: [ {item1 URI}, {item2 URI} ... ]}
  "POSTing a new item saves it to the persister" in new RestApiFixture() {
    val foo = Foo(None, "Baz")
    validator_expects_validate[Foo](Seq(foo), Post, Right(Seq(foo)))
    persister_expects_nextSequenceNumber("555")
    persister_expects_save("spaceship", Seq(foo.withId(Some("555"))))
    serialiser_expects_deserialise[Foo]("<A serialised Foo>", Seq(foo))

    app.post(s"/spaceship", body = "<A serialised Foo>") --> """{"addedCount":"1"}"""
  }

  "POSTing a new item request an ID for it from the Persister" in new RestApiFixture() {
    persister_expects_nextSequenceNumber("666")

    app.post(s"/planet", body = """[{"name": "Earth", "classification": "tolerable"}]""")
  }

  "We send back a 404 for Resource types we don't support" in new RestApiFixture() {
    // TODO - CAS - 20/04/15 - Test this for PUT, POST, etc
    val response = app.get(s"/petName")

    assertFailed(response, 404, "Resource type not supported: petName")
  }

  def assertFailed(response: MockResult, statusCode: Int, message: String): Unit = {
    response.body must include(message)
    response.code must equal(statusCode)
  }

  "POSTing items returns an updated count" in new RestApiFixture() {
    val somePlanets = Seq(
      Planet(None, "Mercury", "bloody hot"),
      Planet(None, "Venus", "also bloody hot")
    )

    val somePlanetsWithIds = Seq(
      Planet(Some("1"), "Mercury", "bloody hot"),
      Planet(Some("2"), "Venus", "also bloody hot")
    )

    serialiser_expects_deserialise[Planet]("<Some serialised Planets>", somePlanets)
    validator_expects_validate[Planet](somePlanets, Post, Right(somePlanetsWithIds))
    persister_expects_nextSequenceNumber("1", "2")
    persister_expects_save("planet", somePlanetsWithIds)

    app.post(s"/planet", body = "<Some serialised Planets>") --> """{"addedCount":"2"}"""
  }

  "Return a 405 for HTTP methods that are not supported" in new RestApiFixture() {
    val response = app.put("/spaceship", body = "")

    assertFailed(response, 405, """Method not allowed: PUT. Methods supported by /spaceship are: GET all, POST""")
  }

  "We can GET a single item by ID" in new RestApiFixture() {
    persister_expects_load("foo", "3", Right(Seq(Foo(Some("1"), "bar"))))
    serialiser_expects_serialiseSingle[Foo]

    app.get("/foo/3") --> "<A serialised Foo>"
  }

  "We can filter the complete list of resources by adding query parameters to a GET" in new RestApiFixture() {
    persister_expects_read("foo", Map("bar" -> "someValue"), Right(seqFoos))
    serialiser_expects_serialise[Foo]

    app.get("/foo?bar=someValue") --> "<A serialised Seq[Foo]>"
  }

  "We can delete a Resource by ID" in new RestApiFixture() {
    persister_expects_delete("foo", "1")

    app.delete(s"/foo/1") --> """{"deletedCount":"1"}"""
  }

  "We can PUT updates to Resources" in new RestApiFixture() {
    val foo = Foo(Some("1"), "Baz")
    serialiser_expects_deserialise[Foo]("<A serialised Foo>", Seq(foo))
    validator_expects_validate[Foo](Seq(foo), Put, Right(Seq(foo)))
    persister_expects_update("foo", Seq(foo))

    app.put(s"/foo/1", body = "<A serialised Foo>") --> """{"updatedCount":"1"}"""
  }

  "Validation failures are returned in the HTTP response with the relevant status code" in new RestApiFixture() {
    val foo = Foo(Some("1"), "Baz")

    serialiser_expects_deserialise[Foo]("<A serialised Foo>", Seq(foo))
    validator_expects_validate[Foo](Seq(foo), Post, Left(Failure(666, "Some reason for the failure")))

    val response = app.post(s"/foo", body = "<A serialised Foo>")

    assertFailed(response, 666, "Some reason for the failure")
  }

  "We can define special query endpoints to allow for custom queries by clients" in {
    // e.g. find all Parties with partyDate in April
    // The query is a Resource: PartiesByMonth(April)
    // For query resources we can register a query function, which returns the target type, e.g. Party
    // Then do a search/filter in T land
    pending
  }

  "We can also POST queries" in {
    pending
  }

  "Exceptions and the relevant JSON fragments are stored as resources" in {
    pending
  }

  "The ID of an exception is returned with the error notification" in {
    pending
  }

  "Migration results are stored as resources" in {
    pending
  }

  "Audit records are stored as resources" in {
    pending
  }

  "We are tolerant of URI-end slashes" in {
    // Use the regex matcher: get("/api/monkey/?")
    pending
  }

  // TODO - CAS - 27/04/15:
  // Caching spec

  class RestApiFixture() {
    val persisterMock: Persister = mock[Persister]
    val formats: Formats = mock[Formats]
    val serialiserMock: Serialiser = mock[Serialiser]
    val validatorMock: Validator = mock[Validator]

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
    val rest = new RestExample("/", controller, serialiserMock, persisterMock, validatorMock)
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

    def serialiser_expects_serialiseSingle[T <: AnyRef : ClassTag] = {
      (serialiserMock.serialise[T](_: T)(_: ClassTag[T])).expects(*, *).returning(s"<A serialised ${Classy.name[T]}>")
    }

    def serialiser_expects_deserialise[T <: Resource[T] : ClassTag](body: String, returns: Seq[T]) = {
      (serialiserMock.deserialise[T](_: String)(_: Manifest[T])).expects(body, *).returning(Right(returns))
    }


    def persister_expects_read[T <: Resource[T] : Manifest](resourceName: String, params: Map[String,String] = Map.empty, returns: Either[Failure, Seq[T]]) = {
      (persisterMock.read[T](_: String, _ : Map[String,String])(_: Manifest[T])).expects(resourceName, params, *).returning(returns)
    }

    def persister_expects_loadOnly[T : Manifest](resourceName: String, params: Map[String, String], returns: Either[Failure, Seq[T]]) = {
      (persisterMock.loadOnly[T](_: String, _ : Map[String, String])(_: Manifest[T])).expects(resourceName, params, *).returning(returns)
    }

    def persister_expects_load[T <: Resource[T]](resourceName: String, key: String, returns: Either[Failure, Seq[T]]) = {
      (persisterMock.load[T](_: String, _: String)(_: Manifest[T])).expects(resourceName, key, *).returning(returns)
    }


    val sequence = new mutable.Queue[String]()
    def persister_expects_nextSequenceNumber(values: String*) = {
      sequence.enqueue(values:_*)
      (persisterMock.nextSequenceId _).expects().onCall(() => sequence.dequeue()).anyNumberOfTimes()
    }

    def persister_expects_save[T <: Resource[T]](resourceName: String, expectedSeq: Seq[T]) = {
      (persisterMock.create[T](_: String, _: Seq[T])(_: Manifest[T])).expects(resourceName, expectedSeq, *).returning(Right(expectedSeq.size))
    }

    def persister_expects_delete[T <: Resource[T]](resourceName: String, id: String) = {
      (persisterMock.delete[T](_: String, _: String)(_: Manifest[T])).expects(resourceName, id, *).returning(Right(1))
    }

    def persister_expects_update[T <: Resource[T]](resourceName: String, expectedSeq: Seq[T]) = {
      (persisterMock.update[T](_: String, _: Seq[T])(_: Manifest[T])).expects(resourceName, expectedSeq, *).returning(Right(expectedSeq.size))
    }

    def validator_expects_validate[T <: Resource[T]](resources: Seq[T], httpMethod: HttpMethod, returns: Either[Failure, Seq[T]]) = {
      (validatorMock.validate[T](_: Seq[T], _: HttpMethod)).expects(resources, httpMethod).returning(returns)
    }
  }
}

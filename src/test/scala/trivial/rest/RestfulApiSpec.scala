package trivial.rest

import com.google.inject.Stage
import com.twitter.finagle.httpx.Status
import com.twitter.finagle.httpx.Status._
import com.twitter.finatra.http.filters.CommonFilters
import com.twitter.finatra.http.routing.HttpRouter
import com.twitter.finatra.http.test.EmbeddedHttpServer
import com.twitter.finatra.http.{Controller, HttpServer}
import com.twitter.inject.server.FeatureTest
import org.jboss.netty.handler.codec.http.HttpHeaders.Names._
import org.json4s.Formats
import org.scalamock.scalatest.MockFactory
import trivial.rest.TestDirectories._
import trivial.rest.controller.finatra.{NonHidingExceptionsMapper, UsableController}
import trivial.rest.persistence.{JsonOnFileSystem, Persister}
import trivial.rest.serialisation.{Json4sSerialiser, Serialiser}
import trivial.rest.validation.{RuleBasedRestValidator, RestValidator}

import scala.collection.mutable
import scala.reflect.ClassTag

class RestfulApiSpec extends FeatureTest with MockFactory {

  private val fixture = new RestApiFixture()
  import fixture._

  val server = fixture.actualServer

  "The root path provides a not-quite-hypertext list of supported resource types" in {
    serialiser_expects_serialise[String](
      Seq("currency", "exchangerate", "foo", "metricperson", "planet", "spaceship", "vector"),
      """["currency","exchangerate","foo","metricperson","planet","spaceship","vector"]"""
    )

    server.httpGet(
      path = "/",
      andExpect = Ok,
      headers = Map(CONTENT_TYPE -> "application/json; charset=UTF-8"),
      withBody = """["currency","exchangerate","foo","metricperson","planet","spaceship","vector"]"""
    )
  }

  val seqFoos = Seq(
    Foo(Some("1"), "bar"),
    Foo(Some("2"), "baz"),
    Foo(Some("3"), "bazaar")
  )

  "Registering a resource type for GET All allows bulk download of JSON data" in {
    persister_expects_read("foo", returns = Right(seqFoos))
    serialiser_expects_serialise[Foo]

    server.httpGet(
      path = "/foo",
      andExpect = Ok,
      withBody = "<A serialised Seq[Foo]>"
    )
  }

  // TODO - CAS - 08/07/15 - Change the return type to: {added: [ {item1 URI}, {item2 URI} ... ]}
  "POSTing a new item saves it to the persister" in {
    val foo = Foo(None, "Baz")
    validator_expects_validate[Foo](Seq(foo), Post, Right(Seq(foo)))
    persister_expects_nextSequenceNumber("555")
    persister_expects_save("spaceship", Seq(foo.withId(Some("555"))))
    serialiser_expects_deserialise[Foo]("<A serialised Foo>", Seq(foo))

    server.httpPost(
      path = "/spaceship",
      postBody = "<A serialised Foo>",
      andExpect = Ok,
      withBody = """{"addedCount":"1"}"""
    )
  }

  "POSTing a new item request an ID for it from the Persister" in {
    persister_expects_nextSequenceNumber("666")

    server.httpPost(
      path = "/planet",
      postBody = """[{"name": "Earth", "classification": "tolerable"}]"""
    )
  }

  "We send back a 404 for Resource types we don't support" in {
    // TODO - CAS - 20/04/15 - Test this for PUT, POST, etc
    server.httpGet(
      path = "/petName",
      andExpect = NotFound,
      withBody = "Resource type not supported: petName"
    )
  }

  "POSTing items returns an updated count" in {
    val somePlanets = Seq(
      Planet(None, "Mercury", "bloody hot"),
      Planet(None, "Venus", "also bloody hot")
    )

    val somePlanetsWithIds = Seq(
      // TODO - CAS - 11/09/15 - The other /planet test is interfering with this one, by
      // setting an infinite nextSequenceNumber expectation. Thus, we are off by one, in
      // the sequence: 666, 1, 2
      Planet(Some("666"), "Mercury", "bloody hot"),
      Planet(Some("1"), "Venus", "also bloody hot")
    )

    serialiser_expects_deserialise[Planet]("<Some serialised Planets>", somePlanets)
    validator_expects_validate[Planet](somePlanets, Post, Right(somePlanetsWithIds))
    persister_expects_nextSequenceNumber("1", "2")
    persister_expects_save("planet", somePlanetsWithIds)

    server.httpPost(
      path = "/planet",
      postBody = "<Some serialised Planets>",
      andExpect = Ok,
      withBody = """{"addedCount":"2"}"""
    )
  }

  "Return a 405 for base-path calls using HTTP methods that are not supported" in {
    server.httpPut(
      path = "/spaceship",
      putBody = """{"validJson":true}""",
      andExpect = MethodNotAllowed,
      withBody = "Method not allowed: PUT. Methods supported by /spaceship are: GET all, POST"
    )
  }

  // TODO - CAS - 11/09/15 - Sort this out
//  "Return a 405 for id-specific calls using HTTP methods that are not supported" in {
//    server.httpPut(
//      path = "/spaceship/1",
//      putBody = "",
//      andExpect = MethodNotAllowed,
//      withBody = "Method not allowed: PUT. Methods supported by /spaceship are: GET all, POST"
//    )
//  }

  "We can GET a single item by ID" in {
    persister_expects_load("foo", "3", Right(Seq(Foo(Some("1"), "bar"))))
    serialiser_expects_serialiseSingle[Foo]

    server.httpGet(
      path = "/foo/3",
      andExpect = Ok,
      withBody = "<A serialised Foo>"
    )
  }

  "We can filter the complete list of resources by adding query parameters to a GET" in {
    persister_expects_read("foo", Map("bar" -> "someValue"), Right(seqFoos))
    serialiser_expects_serialise[Foo]

    server.httpGet(
      path = "/foo?bar=someValue",
      andExpect = Ok,
      withBody = "<A serialised Seq[Foo]>"
    )
  }

  "We can delete a Resource by ID" in {
    persister_expects_delete("foo", "1")

    server.httpDelete(
      path = "/foo/1",
      andExpect = Ok,
      withBody = """{"deletedCount":"1"}"""
    )
  }

  "We can PUT updates to Resources" in {
    val foo = Foo(Some("1"), "Baz")
    serialiser_expects_deserialise[Foo]("<A serialised Foo>", Seq(foo))
    validator_expects_validate[Foo](Seq(foo), Put, Right(Seq(foo)))
    persister_expects_update("foo", Seq(foo))

    server.httpPut(
      path = "/foo/1",
      putBody = "<A serialised Foo>",
      andExpect = Ok,
      withBody = """{"updatedCount":"1"}"""
    )
  }

  "Validation failures are returned in the HTTP response with the relevant status code" in {
    val foo = Foo(Some("1"), "Baz")

    serialiser_expects_deserialise[Foo]("<A serialised Foo>", Seq(foo))
    validator_expects_validate[Foo](Seq(foo), Post, Left(Failure(666, "Some reason for the failure")))

    server.httpPost(
      path = "/foo",
      postBody = "<A serialised Foo>",
      andExpect = Status(666),
      withBody = "Some reason for the failure"
    )
  }

  "We can define special query endpoints to allow for custom queries by clients" in {
    // e.g. find all Parties with partyDate in April
    // The query is a Resource: PartiesByMonth(April)
    // For query resources we can register a query function, which returns the target type, e.g. Party
    // Then do a search/filter in T land
    pending
  }

//  "We can also POST queries" in {
//    pending
//  }
//
//  "Exceptions and the relevant JSON fragments are stored as resources" in {
//    pending
//  }
//
//  "The ID of an exception is returned with the error notification" in {
//    pending
//  }
//
//  "Migration results are stored as resources" in {
//    pending
//  }
//
//  "Audit records are stored as resources" in {
//    pending
//  }
//
//  "We are tolerant of URI-end slashes" in {
//    // Use the regex matcher: get("/api/monkey/?")
//    pending
//  }

  // TODO - CAS - 27/04/15:
  // Caching spec

  class RestApiFixture() {
    val persister: Persister = mock[Persister]
    val formats: Formats = mock[Formats]
    val serialiser: Serialiser = mock[Serialiser]
    val validator: RestValidator = mock[RestValidator]

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

    val docRoot = provisionedTestDir
    val demoApp = new TestFinatraServer(docRoot, "/", serialiser, persister, validator)
    val actualServer = new EmbeddedHttpServer(demoApp)

    def serialiser_expects_registerResource[T <: Resource[T] : ClassTag] = {
      (serialiser.registerResource[T] (_: Formats => Either[Failure, Seq[T]])(_: ClassTag[T])).expects(*,*)
    }

    def serialiser_expects_formatsExcept[T <: Resource[T] : ClassTag] = {
      (serialiser.formatsExcept[T] (_: ClassTag[T])).expects(*).returning(formats).anyNumberOfTimes()
    }

    def serialiser_expects_serialise[T <: AnyRef : ClassTag](input: Seq[T], output: String) = {
      (serialiser.serialise[T](_: Seq[T])(_: ClassTag[T])).expects(input, *).returning(output)
    }

    def serialiser_expects_serialise[T <: AnyRef : ClassTag] = {
      (serialiser.serialise[T](_: Seq[T])(_: ClassTag[T])).expects(*, *).returning(s"<A serialised Seq[${Classy.name[T]}]>")
    }

    def serialiser_expects_serialiseSingle[T <: AnyRef : ClassTag] = {
      (serialiser.serialise[T](_: T)(_: ClassTag[T])).expects(*, *).returning(s"<A serialised ${Classy.name[T]}>")
    }

    def serialiser_expects_deserialise[T <: Resource[T] : ClassTag](body: String, returns: Seq[T]) = {
      (serialiser.deserialise[T](_: String)(_: Manifest[T])).expects(body, *).returning(Right(returns))
    }

    def persister_expects_read[T <: Resource[T] : Manifest](resourceName: String, params: Map[String,String] = Map.empty, returns: Either[Failure, Seq[T]]) = {
      (persister.read[T](_: String, _ : Map[String,String])(_: Manifest[T])).expects(resourceName, params, *).returning(returns)
    }

    def persister_expects_load[T <: Resource[T]](resourceName: String, id: String, returns: Either[Failure, Seq[T]]) = {
      (persister.read[T](_: String, _: String)(_: Manifest[T])).expects(resourceName, id, *).returning(returns)
    }

    val sequence = new mutable.Queue[String]()
    def persister_expects_nextSequenceNumber(values: String*) = {
      sequence.enqueue(values:_*)
      (persister.nextSequenceId _).expects().onCall(() => sequence.dequeue()).anyNumberOfTimes()
    }

    def persister_expects_save[T <: Resource[T]](resourceName: String, expectedSeq: Seq[T]) = {
      (persister.create[T](_: String, _: Seq[T])(_: Manifest[T])).expects(resourceName, expectedSeq, *).returning(Right(expectedSeq.size))
    }

    def persister_expects_delete[T <: Resource[T]](resourceName: String, id: String) = {
      (persister.delete[T](_: String, _: String)(_: Manifest[T])).expects(resourceName, id, *).returning(Right(1))
    }

    def persister_expects_update[T <: Resource[T]](resourceName: String, expectedSeq: Seq[T]) = {
      (persister.update[T](_: String, _: Seq[T])(_: Manifest[T])).expects(resourceName, expectedSeq, *).returning(Right(expectedSeq.size))
    }

    def validator_expects_validate[T <: Resource[T]](resources: Seq[T], httpMethod: HttpMethod, returns: Either[Failure, Seq[T]]) = {
      (validator.validate[T](_: Seq[T], _: HttpMethod)).expects(resources, httpMethod).returning(returns)
    }
  }
}

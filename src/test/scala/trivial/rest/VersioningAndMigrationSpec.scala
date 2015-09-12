package trivial.rest

import com.google.inject.Stage
import com.twitter.finagle.httpx.Status._
import com.twitter.finatra.http.test.EmbeddedHttpServer
import com.twitter.inject.server.FeatureTest
import org.json4s.NoTypeHints
import org.json4s.native.Serialization
import org.scalatest.{MustMatchers, OneInstancePerTest}
import org.scalatest.MustMatchers._
import trivial.rest.TestDirectories._
import trivial.rest.persistence.JsonOnFileSystem
import trivial.rest.serialisation.Json4sSerialiser
import trivial.rest.validation.RuleBasedRestValidator

/**
    USE CASES
                                                      Per-record   Requires Migration   Breaking change?

(1) Add a new field (and a default)                   -            -                    Non-breaking, if client is tolerant reader
(2) Change a field's data, based on other fields      Yes          Yes                  Non-breaking
(3) Change resource name, and map from new resource   -            Yes                  Non-breaking, requires an alias
(4) Change resource name                              -            Yes                  Breaks GET and POST
(5) Change a field's type, e.g. Int to BigDecimal     -            Yes                  Breaks GET, POST requires a migration
(6) Remove a field                                    -            -                    Breaks GET

 */
class VersioningAndMigrationSpec extends FeatureTest with OneInstancePerTest {

  val docRoot = provisionedTestDir
  val serialiser = new Json4sSerialiser
  val persister = new JsonOnFileSystem(docRoot, serialiser)
  val validator = new RuleBasedRestValidator()
  private val demoApp = new TestFinatraServer(docRoot, "/", serialiser, persister, validator)

  val server = new EmbeddedHttpServer(twitterServer = demoApp, stage = Stage.PRODUCTION)

  "(1) Add a field with a sensible default - post a Currency without the required 'symbol' field" in {
    val newCurrency = """[{"isoName":"NZD"}]"""

    serialiser.withDefaultFields[Currency](Currency(None, "", ""))

    server.httpPost(
      path = "/currency",
      postBody = newCurrency,
      andExpect = Ok,
      withBody = """{"addedCount":"1"}"""
    )
  }

  "(2)(a) Change a field's data, based on other fields - add some symbols to existing currencies" in {
    val oldData = Seq(
      Currency(None, "AUD", ""),
      Currency(None, "ABC", ""),
      Currency(None, "XXX", ""))

    val migratedData = Seq(
      Currency(None, "AUD", "$"),
      Currency(None, "ABC", ""),
      Currency(None, "XXX", "X"))

    givenExistingData("currency", oldData)

    val migrationResult = demoApp.rest.migrate[Currency](forwardMigration = {
      case Currency(id, code, _) if code.endsWith("D") => Currency(id, code, "$")
      case Currency(id, "XXX", _) => Currency(id, "XXX", "X")
      case other => other
    })

    migrationResult mustEqual Right(3)

    server.httpGet(
      path = "/currency",
      andExpect = Ok,
      withBody = jsonFor(migratedData)
    )
  }

  case class NotACurrency(dog: String, cat: Int)

  "(2)(b) Change a field's data, based on other fields - migration failures are returned to the caller" in {
    givenExistingData("currency", Seq(NotACurrency("X", 2)))

    demoApp.rest.migrate[Currency]()
      .right.map(i => fail("Should have bailed"))
      .left.map(f => f.describe must startWith ("No usable value for isoName"))
  }

  // Requires (a) an Auxiliary constructor and (b) a companion object to be declared, even if it is empty.
  "(3)(a) Change resource name, and map from new resource --> migrate old data" in {
    val imperialPerson = ImperialPerson(None, "Bob", 73, 220)
    val metricPerson = MetricPerson(None, "Bob", 185, 29.1)

    givenExistingData("imperialperson", Seq(imperialPerson))

    demoApp.rest.migrate[MetricPerson](oldResourceName = Some("imperialperson")) mustEqual Right(1)

    server.httpGet(
      path = "/metricperson",
      andExpect = Ok,
      withBody = jsonFor(Seq(metricPerson))
    )
  }

  // TODO - CAS - 11/06/15 - Split into two tests: one for adding an alias, the other for backwards-compatibility with an older data format
  "(3)(b) Change resource name, and map from new resource --> maintain backwards-compatibility for GET" in {
    givenExistingData("imperialperson", Seq(ImperialPerson(None, "Bob", 73, 220)))

    demoApp.rest.migrate[MetricPerson](
      oldResourceName = Some("imperialperson"),
      backwardsView = ImperialAndMetricConverter.viewAsImperial) mustEqual Right(1)

    server.httpGet(
      path = "/imperialperson",
      andExpect = Ok,
      withBody = jsonFor(Seq(ImperialPerson(None, "Bob", 73, 219)))
    )
  }

  "(3)(c) Change resource name, and map from new resource --> maintain backwards-compatibility for POST" in {
    givenExistingData("imperialperson", Seq())

    demoApp.rest.migrate[MetricPerson](
      oldResourceName = Some("imperialperson"),
      backwardsView = ImperialAndMetricConverter.viewAsImperial) mustEqual Right(0)

    server.httpPost(
      path = "/imperialperson",
      postBody = jsonFor(Seq(ImperialPerson(None, "Sue", 60, 170))),
      andExpect = Ok,
      withBody = """{"addedCount":"1"}"""
    )

    server.httpGet(
      path = "/metricperson",
      andExpect = Ok,
      withBody = jsonFor(Seq(MetricPerson(Some("0000101"), "Sue", 152, BigDecimal("33.3"))))
    )
  }

  "If there is no data to migrate, we receive an update count of zero, not a failure" in {
    demoApp.rest.migrate[Foo]() mustEqual Right(0)
  }

  "Exceptions during data migration result in Failures" in {
    givenExistingData("currency", Seq(Currency(None, "ABC", "")))

    demoApp.rest.migrate[Currency](forwardMigration = t => throw new RuntimeException("Monkeys ate your data"))
      .right.map(i => fail("Should have bailed"))
      .left.map(f => f.describe must startWith ("Migration failed, due to: java.lang.RuntimeException: Monkeys ate your data"))
  }

  val planets = Seq(
    Planet(None, "Mercury", "Quite warm"),
    Planet(None, "Venus", "Tropical"),
    Planet(None, "Earth", "Intemperate"),
    Planet(None, "Mars", "Crisp mornings")
  )

  def withIds(ps: Seq[Planet], ids: Seq[String]): Seq[Planet] = planets.zip(ids).map(pi => pi._1.withId(Some(pi._2)))

  "Client code can pre-populate resources, so they are not empty when they are first released" in {
    demoApp.rest.prepopulate[Planet](planets) mustEqual Right(4)

    server.httpGet(
      path = "/planet",
      andExpect = Ok,
      withBody = jsonFor(withIds(planets, Seq("0000101", "0000102", "0000103", "0000104")))
    )
  }

  "Prepopulated data is only added once" in {
    givenExistingData("planet", Seq(Planet(Some("XXX"), "Mercury", "Quite warm"), Planet(Some("YYY"), "Venus", "Tropical")))

    demoApp.rest.prepopulate[Planet](planets) mustEqual Right(2)
    demoApp.rest.prepopulate[Planet](planets) mustEqual Right(0)

    server.httpGet(
      path = "/planet",
      andExpect = Ok,
      withBody = jsonFor(withIds(planets, Seq("XXX", "YYY", "0000101", "0000102")))
    )
  }

  "We can pre-populate hard-coded Resources" in {
    demoApp.rest.prepopulate[Gender](Seq(Gender(true), Gender(false))) mustEqual Right(2)
    demoApp.rest.prepopulate[Gender](Seq(Gender(true), Gender(false))) mustEqual Right(0)
  }

  def jsonFor[T](seqTs: Seq[T]): String = Serialization.write(seqTs)(Serialization.formats(NoTypeHints))

  def givenExistingData[T <: AnyRef](resourceName: String, resources: T) = {
    val json = Serialization.write(resources)(Serialization.formats(NoTypeHints))
    persister.assuredFile(docRoot, resourceName).delete()
    persister.assuredFile(docRoot, resourceName).writeAll(json)
  }
}
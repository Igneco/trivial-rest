package trivial.rest

import com.twitter.finatra.test.SpecHelper
import org.json4s.NoTypeHints
import org.json4s.native.Serialization
import org.scalatest.{OneInstancePerTest, MustMatchers, WordSpec}

import scala.reflect.ClassTag
import scala.tools.nsc.FatalError

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
class VersioningAndMigrationSpec extends WordSpec with MustMatchers with SpecHelper with OneInstancePerTest {

  override val server = new TestableFinatraServer()

  "(1) Add a field with a sensible default - post a Currency without the required 'symbol' field" in {
    val newCurrency = """[{"isoName":"NZD"}]"""

    server.serialiser.withDefaultFields[Currency](Currency(None, "", ""))

    post("/currency", body = newCurrency)

    response.body must equal("""{"addedCount":"1"}""")
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

    val migrationResult = server.rest.migrate[Currency](forwardMigration = {
      case Currency(id, code, _) if code.endsWith("D") => Currency(id, code, "$")
      case Currency(id, "XXX", _) => Currency(id, "XXX", "X")
      case other => other
    })

    migrationResult mustEqual Right(3)

    get("/currency")

    response.body mustEqual jsonFor(migratedData)
  }

  case class NotACurrency(dog: String, cat: Int)

  "(2)(b) Change a field's data, based on other fields - migration failures are returned to the caller" in {
    givenExistingData("currency", Seq(NotACurrency("X", 2)))

    server.rest.migrate[Currency]()
      .right.map(i => fail("Should have bailed"))
      .left.map(f => f.reason must startWith ("No usable value for isoName"))
  }

  // Requires (a) an Auxiliary constructor and (b) a companion object to be declared, even if it is empty.
  "(3)(a) Change resource name, and map from new resource --> migrate old data" in {
    val imperialPerson = ImperialPerson(None, "Bob", 73, 220)
    val metricPerson = MetricPerson(None, "Bob", 185, 29.1)

    givenExistingData("imperialperson", Seq(imperialPerson))

    server.rest.migrate[MetricPerson](oldResourceName = Some("imperialperson")) mustEqual Right(1)

    get("/metricperson")

    response.body mustEqual jsonFor(Seq(metricPerson))
  }

  // TODO - CAS - 11/06/15 - Split into two tests: one for adding an alias, the other for backwards-compatibility with an older data format
  "(3)(b) Change resource name, and map from new resource --> maintain backwards-compatibility for GET" in {
    givenExistingData("imperialperson", Seq(ImperialPerson(None, "Bob", 73, 220)))

    server.rest.migrate[MetricPerson](
      oldResourceName = Some("imperialperson"),
      backwardsView = ImperialAndMetricConverter.viewAsImperial) mustEqual Right(1)

    get("/imperialperson")

    // Bob is now slightly lighter, due to a fortuitous loss of precision in my slightly spurious conversion code
    response.body mustEqual jsonFor(Seq(ImperialPerson(None, "Bob", 73, 219)))
  }

  "(3)(c) Change resource name, and map from new resource --> maintain backwards-compatibility for POST" in {
    givenExistingData("imperialperson", Seq())

    server.rest.migrate[MetricPerson](
      oldResourceName = Some("imperialperson"),
      backwardsView = ImperialAndMetricConverter.viewAsImperial) mustEqual Right(0)

    post("/imperialperson", body = jsonFor(Seq(ImperialPerson(None, "Sue", 60, 170))))
    response.code mustEqual 200

    get("/metricperson")
    response.body mustEqual jsonFor(Seq(MetricPerson(Some("0000101"), "Sue", 152, BigDecimal("33.3"))))
  }

  "If there is no data to migrate, we receive an update count of zero, not a failure" in {
    server.rest.migrate[Foo]() mustEqual Right(0)
  }

  "Exceptions during data migration result in Failures" in {
    givenExistingData("currency", Seq(Currency(None, "ABC", "")))

    server.rest.migrate[Currency](forwardMigration = t => throw new RuntimeException("Monkeys ate your data"))
      .right.map(i => fail("Should have bailed"))
      .left.map(f => f.reason must startWith ("Migration failed, due to: java.lang.RuntimeException: Monkeys ate your data"))
  }

  val planets = Seq(
    Planet(None, "Mercury", "Quite warm"),
    Planet(None, "Venus", "Tropical"),
    Planet(None, "Earth", "Intemperate"),
    Planet(None, "Mars", "Crisp mornings")
  )

  def withIds(ps: Seq[Planet], ids: Seq[String]): Seq[Planet] = planets.zip(ids).map(pi => pi._1.withId(pi._2))

  "Client code can pre-populate resources, so they are not empty when they are first released" in {
    server.rest.prepopulate[Planet](planets) mustEqual Right(4)

    get("/planet")

    response.body mustEqual jsonFor(withIds(planets, Seq("0000101", "0000102", "0000103", "0000104")))
  }

  "Prepopulated data is only added once" in {
    pending
    givenExistingData("planet", Seq(Planet(Some("6"), "Mercury", "Quite warm"), Planet(Some("7"), "Venus", "Tropical")))

    server.rest.prepopulate[Planet](planets) mustEqual Right(2)

    get("/planet")

    response.body mustEqual jsonFor(withIds(planets, Seq("0000106", "0000107", "0000101", "0000102")))
  }

  def jsonFor[T](seqTs: Seq[T]): String = Serialization.write(seqTs)(Serialization.formats(NoTypeHints))

  def givenExistingData[T <: AnyRef](resourceName: String, resources: T) = {
    val json = Serialization.write(resources)(Serialization.formats(NoTypeHints))
    server.persister.assuredFile(server.testDir, resourceName).delete()
    server.persister.assuredFile(server.testDir, resourceName).writeAll(json)
  }
}
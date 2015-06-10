package trivial.rest

import com.twitter.finatra.test.SpecHelper
import org.json4s.NoTypeHints
import org.json4s.native.Serialization
import org.scalatest.{MustMatchers, WordSpec}

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

trait Migration[Before, After] {
  def convert(before: Before, after: After): After
}


 */
class VersioningAndMigrationSpec extends WordSpec with MustMatchers with SpecHelper {

  override val server = new TestableFinatraServer {}

  "(1) Add a field with a sensible default - post a Currency without the required 'symbol' field" in {
    val newCurrency = """[{"isoName":"NZD"}]"""

    server.serialiser.withDefaultFields[Currency](Currency(None, "", ""))

    post("/currency", body = newCurrency)

    response.body must equal("""{"addedCount":"1"}""")
  }

  "(2) Change a field's data, based on other fields - add some symbols to existing currencies" in {
    val oldData = Seq(
      Currency(None, "AUD", ""),
      Currency(None, "ABC", ""),
      Currency(None, "XXX", ""))

    val migratedData = Seq(
      Currency(None, "AUD", "$"),
      Currency(None, "ABC", ""),
      Currency(None, "XXX", "X"))

    givenExistingData("currency", oldData)

    server.rest.migrate[Currency]{
      case Currency(id, code, _) if code.endsWith("D") => Currency(id, code, "$")
      case Currency(id, "XXX", _) => Currency(id, "XXX", "X")
      case other => other
    }

    get("/currency")

    response.body mustEqual jsonFor(migratedData)
  }

  "(2)(b) Change a field's data, based on other fields - migration failures cause the system to exit" in {
    givenExistingData("currency", Seq(Currency(None, "AUD", "")))

    try {
      server.rest.migrate[Currency](identity, Some("incorrectResourceName"))
      fail("Should have bailed")
    } catch {
      case t: Throwable => 
    }
  }

  "(3)(a) Change resource name, and map from new resource --> migrate old data" in {
    val imperialPerson = ImperialPerson(None, "Bob", 73, 220)
    val metricPerson = MetricPerson(None, "Bob", 185, 20.5)

    givenExistingData("imperialperson", Seq(imperialPerson))

    server.rest.migrate[MetricPerson](identity, Some("imperialperson"))

    get("/metricperson")

    response.body mustEqual jsonFor(Seq(metricPerson))
  }

  "(3)(b) Change resource name, and map from new resource --> maintain backwards-compatibility for GET" in {
    val imperialPerson = ImperialPerson(None, "Bob", 73, 220)
    val metricPerson = MetricPerson(None, "Bob", 185, 20.5)

    givenExistingData("metricperson", Seq(metricPerson))

    get("/imperialperson")

    response.body mustEqual jsonFor(Seq(imperialPerson))
  }

  "(3)(c) Change resource name, and map from new resource --> maintain backwards-compatibility for POST" in { fail("Poo") }

  "We only run the migration of stored data once per JVM runtime" in { fail("We actually run it every time") }

  def jsonFor[T <: Resource[T] : ClassTag](seqTs: Seq[T]): String = Serialization.write(seqTs)(Serialization.formats(NoTypeHints))

  def givenExistingData[T <: Resource[T] : ClassTag : Manifest](resourceName: String, resources: Seq[T]) = {
    implicit val formats = server.serialiser.formatsExcept[T]
    server.persister.assuredFile(server.testDir, resourceName).delete()
    server.persister.save[T](resourceName, resources)
  }
}
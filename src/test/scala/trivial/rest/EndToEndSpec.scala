package trivial.rest

import com.twitter.finatra.FinatraServer
import com.twitter.finatra.test.SpecHelper
import org.jboss.netty.handler.codec.http.HttpHeaders.Names._
import org.scalatest.{BeforeAndAfterAll, MustMatchers, WordSpec}
import trivial.rest.TestDirectories._
import trivial.rest.persistence.{FileSystem, JsonOnFileSystem}
import trivial.rest.serialisation.Json4sSerialiser

import scala.reflect.io.{File, Directory}

class EndToEndSpec extends WordSpec with MustMatchers with SpecHelper {

  val existingCurrencies = """{"id":"1","rate":33.3,"currency":"2"},{"id":"2","rate":44.4,"currency":"3"}"""

  override def server = new FinatraServer {
    private val serialiser = new Json4sSerialiser
    private val testDir = provisionedTestDir
    register(new RestfulControllerExample(serialiser, new JsonOnFileSystem(testDir, serialiser)))
  }

  "Charset for JSON data is UTF-8" in {
    get("/foo")

    response.getHeader(CONTENT_TYPE) must equal("application/json; charset=UTF-8")
  }

  "We can get all ExchangeRates" in {
    get("/exchangerate")

    response.body must equal(s"""[$existingCurrencies]""")
  }

  "We can post an ExchangeRate" in {
    val rate = """[{"rate":55.5,"currency":"1"}]"""

    post("/exchangerate", body = rate)

    response.body must equal("""{"addedCount":"1"}""")
  }

  "We can post a Currency without the required 'symbol' field" in {
    val newCurrency = """[{"isoName":"NZD"}]"""

    post("/currency", body = newCurrency)

    response.body must equal("""{"addedCount":"1"}""")
  }
}